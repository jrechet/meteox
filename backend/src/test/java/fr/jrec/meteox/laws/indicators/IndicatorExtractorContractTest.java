package fr.jrec.meteox.laws.indicators;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.indicators.backend.ClaudeApiExtractor;
import fr.jrec.meteox.laws.indicators.backend.ClaudeCliExtractor;
import fr.jrec.meteox.laws.indicators.backend.OllamaExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Acceptance issue #4 : les 3 backends (claude-cli, claude-api, ollama) passent le MÊME contrat de
 * test, sur réponses enregistrées/mockées — AUCUN appel réseau réel. Corpus : les 4 lois vérifiées
 * (textes sources lus depuis src/lib/laws.js, sorties modèle archivées dans
 * src/test/resources/fixtures/indicators/outputs/).
 */
class IndicatorExtractorContractTest {

  private static final List<String> BACKENDS = List.of("claude-cli", "claude-api", "ollama");
  private static final List<String> CORPUS =
      List.of("eco-agri-1", "eco-eau-1", "eco-canicule-1", "eco-agri-loa");
  private static final Path FIXTURES =
      Path.of("src", "test", "resources", "fixtures", "indicators", "outputs", "claude-cli");
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static WireMockServer wireMock;
  private static Map<String, LawText> corpus;

  @TempDir static Path tempDir;

  @BeforeAll
  static void setUp() throws Exception {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
    corpus = loadCorpusFromLawsJs();
    assertEquals(4, corpus.size(), "les 4 lois vérifiées de laws.js sont attendues");
  }

  @AfterAll
  static void tearDown() {
    wireMock.stop();
  }

  @ParameterizedTest(name = "backend {0} : les 4 lois du corpus produisent 4 axes valides")
  @ValueSource(strings = {"claude-cli", "claude-api", "ollama"})
  void extracts_valid_scores_for_the_whole_corpus(String backend) throws Exception {
    for (String lawId : CORPUS) {
      LawText law = corpus.get(lawId);
      String recorded = Files.readString(FIXTURES.resolve(lawId + ".json"));

      IndicatorScores scores = extractor(backend, recorded).extract(law);

      assertEquals(lawId, scores.lawId());
      assertEquals(4, scores.axes().size(), backend + "/" + lawId);
      for (AxisScore axis : scores.axes()) {
        assertTrue(axis.score() >= -2 && axis.score() <= 2, backend + "/" + lawId);
        assertFalse(axis.justification().isBlank(), backend + "/" + lawId);
        assertFalse(axis.citation().isBlank(), backend + "/" + lawId);
        assertTrue(
            List.of("faible", "moyenne", "haute").contains(axis.confidence()),
            backend + "/" + lawId);
      }
    }
  }

  @ParameterizedTest(name = "backend {0} : score hors bornes → rejet automatique")
  @ValueSource(strings = {"claude-cli", "claude-api", "ollama"})
  void rejects_out_of_bounds_score(String backend) throws Exception {
    LawText law = corpus.get("eco-agri-1");
    String tampered =
        Files.readString(FIXTURES.resolve("eco-agri-1.json")).replace("\"score\": 1.5", "\"score\": 7");

    IndicatorExtractor extractor = extractor(backend, tampered);
    var e = assertThrows(IndicatorExtractionException.class, () -> extractor.extract(law));
    assertTrue(e.getMessage().contains("hors bornes"), backend + " : " + e.getMessage());
  }

  @ParameterizedTest(name = "backend {0} : justification sans citation exacte → rejet automatique")
  @ValueSource(strings = {"claude-cli", "claude-api", "ollama"})
  void rejects_missing_citation(String backend) throws Exception {
    LawText law = corpus.get("eco-agri-1");
    String tampered =
        Files.readString(FIXTURES.resolve("eco-agri-1.json"))
            .replace(
                "restreindre la fabrication et la vente de produits contenant des PFAS",
                "une phrase inventée qui ne figure pas dans le texte source");

    IndicatorExtractor extractor = extractor(backend, tampered);
    var e = assertThrows(IndicatorExtractionException.class, () -> extractor.extract(law));
    assertTrue(e.getMessage().contains("citation"), backend + " : " + e.getMessage());
  }

  @ParameterizedTest(name = "backend {0} : sortie non JSON → rejet")
  @ValueSource(strings = {"claude-cli", "claude-api", "ollama"})
  void rejects_non_json_output(String backend) throws Exception {
    LawText law = corpus.get("eco-agri-1");
    IndicatorExtractor extractor = extractor(backend, "Désolé, je ne peux pas évaluer ce texte.");
    assertThrows(IndicatorExtractionException.class, () -> extractor.extract(law));
  }

  /**
   * Construit un backend alimenté par une réponse enregistrée : stub processus local pour
   * claude-cli, WireMock pour claude-api (enveloppe Messages Anthropic) et ollama (enveloppe
   * /api/chat). Même contenu, trois transports.
   */
  private static IndicatorExtractor extractor(String backend, String recordedOutput)
      throws Exception {
    assertTrue(BACKENDS.contains(backend));
    String base = "http://localhost:" + wireMock.port();
    switch (backend) {
      case "claude-cli" -> {
        Path stub = tempDir.resolve("cli-output-" + recordedOutput.hashCode() + ".txt");
        Files.writeString(stub, recordedOutput);
        return new ClaudeCliExtractor("/bin/cat " + stub.toAbsolutePath(), TIMEOUT);
      }
      case "claude-api" -> {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("stop_reason", "end_turn");
        ObjectNode block = envelope.putArray("content").addObject();
        block.put("type", "text");
        block.put("text", recordedOutput);
        wireMock.resetAll();
        wireMock.stubFor(
            post(urlEqualTo("/v1/messages")).willReturn(okJson(MAPPER.writeValueAsString(envelope))));
        return new ClaudeApiExtractor(base, "test-key-not-a-secret", "claude-sonnet-5", TIMEOUT);
      }
      default -> {
        ObjectNode envelope = MAPPER.createObjectNode();
        ObjectNode message = envelope.putObject("message");
        message.put("role", "assistant");
        message.put("content", recordedOutput);
        wireMock.resetAll();
        wireMock.stubFor(
            post(urlEqualTo("/api/chat")).willReturn(okJson(MAPPER.writeValueAsString(envelope))));
        return new OllamaExtractor(base, "llama3.1", TIMEOUT);
      }
    }
  }

  /** Textes sources = résumés vérifiés de src/lib/laws.js (mêmes données que la base, cf. V2). */
  private static Map<String, LawText> loadCorpusFromLawsJs() throws Exception {
    String js = Files.readString(Path.of("..", "src", "lib", "laws.js"));
    int marker = js.indexOf("LAWS_DATA = [");
    String array = js.substring(js.indexOf('[', marker), js.lastIndexOf(']') + 1);
    ObjectMapper lenient =
        JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .build();
    var out = new LinkedHashMap<String, LawText>();
    for (JsonNode law : lenient.readTree(array)) {
      out.put(
          law.path("id").asText(),
          new LawText(
              law.path("id").asText(), law.path("title").asText(), law.path("summary").asText()));
    }
    return out;
  }
}

package fr.jrec.meteox.laws.indicators.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.jrec.meteox.laws.indicators.IndicatorExtractionException;
import fr.jrec.meteox.laws.indicators.IndicatorExtractor;
import fr.jrec.meteox.laws.indicators.IndicatorPrompt;
import fr.jrec.meteox.laws.indicators.IndicatorResponseParser;
import fr.jrec.meteox.laws.indicators.IndicatorScores;
import fr.jrec.meteox.laws.indicators.LawText;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Backend `claude-api` : API Anthropic Messages ({@code POST /v1/messages}). La clé n'est JAMAIS
 * en dur : elle vient de la config {@code meteox.ai.claude-api.key}, alimentée par le secret
 * d'environnement {@code MX_ANTHROPIC_API_KEY}. Modèle par défaut : claude-sonnet-5 (pas de
 * paramètres d'échantillonnage : rejetés par cette génération de modèles).
 */
public final class ClaudeApiExtractor implements IndicatorExtractor {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MAX_TOKENS = 2048;

  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final Duration timeout;
  private final HttpClient client;

  public ClaudeApiExtractor(String baseUrl, String apiKey, String model, Duration timeout) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "Backend claude-api sélectionné sans clé API (meteox.ai.claude-api.key / MX_ANTHROPIC_API_KEY)");
    }
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.apiKey = apiKey;
    this.model = model;
    this.timeout = timeout;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  }

  @Override
  public String backendName() {
    return "claude-api";
  }

  @Override
  public IndicatorScores extract(LawText law) {
    String output = call(law);
    return IndicatorResponseParser.parse(output, law, "claude-api:" + model);
  }

  private String call(LawText law) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .header("x-api-key", apiKey)
              .header("anthropic-version", "2023-06-01")
              .POST(HttpRequest.BodyPublishers.ofString(payload(law)))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IndicatorExtractionException(
            law.lawId() + " : API Anthropic HTTP " + response.statusCode() + " — " + response.body());
      }
      JsonNode body = MAPPER.readTree(response.body());
      if ("refusal".equals(body.path("stop_reason").asText())) {
        throw new IndicatorExtractionException(law.lawId() + " : requête refusée par l'API");
      }
      for (JsonNode block : body.path("content")) {
        if ("text".equals(block.path("type").asText())) {
          return block.path("text").asText();
        }
      }
      throw new IndicatorExtractionException(law.lawId() + " : réponse API sans bloc texte");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IndicatorExtractionException(law.lawId() + " : appel API interrompu", e);
    } catch (IndicatorExtractionException e) {
      throw e;
    } catch (Exception e) {
      throw new IndicatorExtractionException(law.lawId() + " : appel API Anthropic impossible", e);
    }
  }

  private String payload(LawText law) throws Exception {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("model", model);
    node.put("max_tokens", MAX_TOKENS);
    node.put("system", IndicatorPrompt.SYSTEM);
    ObjectNode message = node.putArray("messages").addObject();
    message.put("role", "user");
    message.put("content", IndicatorPrompt.user(law));
    return MAPPER.writeValueAsString(node);
  }
}

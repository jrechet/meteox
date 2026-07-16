package fr.jrec.meteox.laws.indicators.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * Backend `ollama` : LLM local via l'API HTTP d'Ollama ({@code POST /api/chat}, stream désactivé,
 * sortie contrainte JSON). Gratuit mais qualité moindre sur texte législatif — réservé au dev
 * (plan § 3.2).
 */
public final class OllamaExtractor implements IndicatorExtractor {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String baseUrl;
  private final String model;
  private final Duration timeout;
  private final HttpClient client;

  public OllamaExtractor(String baseUrl, String model, Duration timeout) {
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.model = model;
    this.timeout = timeout;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  }

  @Override
  public String backendName() {
    return "ollama";
  }

  @Override
  public IndicatorScores extract(LawText law) {
    String output = call(law);
    return IndicatorResponseParser.parse(output, law, "ollama:" + model);
  }

  private String call(LawText law) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload(law)))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IndicatorExtractionException(
            law.lawId() + " : Ollama HTTP " + response.statusCode() + " — " + response.body());
      }
      JsonNode body = MAPPER.readTree(response.body());
      String content = body.path("message").path("content").asText("");
      if (content.isBlank()) {
        throw new IndicatorExtractionException(law.lawId() + " : réponse Ollama vide");
      }
      return content;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IndicatorExtractionException(law.lawId() + " : appel Ollama interrompu", e);
    } catch (IndicatorExtractionException e) {
      throw e;
    } catch (Exception e) {
      throw new IndicatorExtractionException(law.lawId() + " : appel Ollama impossible", e);
    }
  }

  private String payload(LawText law) throws Exception {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("model", model);
    node.put("stream", false);
    node.put("format", "json");
    ArrayNode messages = node.putArray("messages");
    ObjectNode system = messages.addObject();
    system.put("role", "system");
    system.put("content", IndicatorPrompt.SYSTEM);
    ObjectNode user = messages.addObject();
    user.put("role", "user");
    user.put("content", IndicatorPrompt.user(law));
    return MAPPER.writeValueAsString(node);
  }
}

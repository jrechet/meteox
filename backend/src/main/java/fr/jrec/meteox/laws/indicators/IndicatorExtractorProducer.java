package fr.jrec.meteox.laws.indicators;

import fr.jrec.meteox.laws.indicators.backend.ClaudeApiExtractor;
import fr.jrec.meteox.laws.indicators.backend.ClaudeCliExtractor;
import fr.jrec.meteox.laws.indicators.backend.OllamaExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Sélectionne le backend IA par configuration seule (aucune recompilation) :
 * {@code quarkus.laws.ai.backend=claude-cli|claude-api|ollama} (acceptance issue #4).
 */
@ApplicationScoped
public class IndicatorExtractorProducer {

  @ConfigProperty(name = "quarkus.laws.ai.backend", defaultValue = "claude-cli")
  String backend;

  @ConfigProperty(name = "meteox.ai.timeout-seconds", defaultValue = "180")
  long timeoutSeconds;

  @ConfigProperty(name = "meteox.ai.claude-cli.command", defaultValue = "claude -p")
  String claudeCliCommand;

  @ConfigProperty(name = "meteox.ai.claude-api.base-url", defaultValue = "https://api.anthropic.com")
  String claudeApiBaseUrl;

  /** Clé API : uniquement via secret d'environnement (MX_ANTHROPIC_API_KEY), jamais en dur. */
  @ConfigProperty(name = "meteox.ai.claude-api.key")
  Optional<String> claudeApiKey;

  @ConfigProperty(name = "meteox.ai.claude-api.model", defaultValue = "claude-sonnet-5")
  String claudeApiModel;

  @ConfigProperty(name = "meteox.ai.ollama.base-url", defaultValue = "http://localhost:11434")
  String ollamaBaseUrl;

  @ConfigProperty(name = "meteox.ai.ollama.model", defaultValue = "llama3.1")
  String ollamaModel;

  @Produces
  @ApplicationScoped
  public IndicatorExtractor extractor() {
    Duration timeout = Duration.ofSeconds(timeoutSeconds);
    return switch (backend) {
      case "claude-cli" -> new ClaudeCliExtractor(claudeCliCommand, timeout);
      case "claude-api" ->
          new ClaudeApiExtractor(claudeApiBaseUrl, claudeApiKey.orElse(""), claudeApiModel, timeout);
      case "ollama" -> new OllamaExtractor(ollamaBaseUrl, ollamaModel, timeout);
      default ->
          throw new IllegalStateException(
              "quarkus.laws.ai.backend inconnu : « "
                  + backend
                  + " » (attendu : claude-cli | claude-api | ollama)");
    };
  }
}

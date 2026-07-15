package fr.jrec.meteox.laws.indicators.backend;

import fr.jrec.meteox.laws.indicators.IndicatorExtractionException;
import fr.jrec.meteox.laws.indicators.IndicatorExtractor;
import fr.jrec.meteox.laws.indicators.IndicatorPrompt;
import fr.jrec.meteox.laws.indicators.IndicatorResponseParser;
import fr.jrec.meteox.laws.indicators.IndicatorScores;
import fr.jrec.meteox.laws.indicators.LawText;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Backend `claude-cli` : exécution headless du CLI Claude (`claude -p`), prompt sur stdin.
 * Utilise l'abonnement local (aucune clé API) — cible : serveur jrec.fr / poste de dev.
 * La commande est entièrement configurable ({@code meteox.ai.claude-cli.command}), ce qui permet
 * aussi de la substituer par un stub dans le contrat de test (aucun appel réseau en test).
 */
public final class ClaudeCliExtractor implements IndicatorExtractor {

  private final List<String> command;
  private final Duration timeout;

  public ClaudeCliExtractor(String commandLine, Duration timeout) {
    this.command = List.of(commandLine.trim().split("\\s+"));
    this.timeout = timeout;
  }

  @Override
  public String backendName() {
    return "claude-cli";
  }

  @Override
  public IndicatorScores extract(LawText law) {
    String prompt = IndicatorPrompt.SYSTEM + "\n\n" + IndicatorPrompt.user(law);
    String output = run(prompt, law);
    return IndicatorResponseParser.parse(output, law, "claude-cli");
  }

  private String run(String prompt, LawText law) {
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
      try (var stdin = process.getOutputStream()) {
        stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
      } catch (java.io.IOException ignored) {
        // Le processus peut ne pas consommer stdin (stub de test, commande avec prompt en argument).
      }
      String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        throw new IndicatorExtractionException(
            law.lawId() + " : claude-cli sans réponse après " + timeout.toSeconds() + "s");
      }
      if (process.exitValue() != 0) {
        throw new IndicatorExtractionException(
            law.lawId() + " : claude-cli code retour " + process.exitValue() + " — " + stderr);
      }
      return stdout;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IndicatorExtractionException(law.lawId() + " : claude-cli interrompu", e);
    } catch (IndicatorExtractionException e) {
      throw e;
    } catch (Exception e) {
      throw new IndicatorExtractionException(law.lawId() + " : lancement claude-cli impossible", e);
    }
  }
}

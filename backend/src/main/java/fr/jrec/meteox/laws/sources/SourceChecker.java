package fr.jrec.meteox.laws.sources;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Vérifie une URL officielle : statut HTTP ET présence du fragment de titre attendu.
 * Réplique la logique de scripts/check-sources.mjs (un 200 seul ne suffit pas —
 * l'audit du 2026-07-14 a trouvé des 200 pointant vers de mauvais dossiers).
 */
@ApplicationScoped
public class SourceChecker {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final String USER_AGENT =
      "Mozilla/5.0 (compatible; meteox-source-check; +https://jrechet.github.io/meteox/)";

  private final HttpClient client =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(TIMEOUT)
          .build();

  /** Résultat d'une vérification (httpStatus nul si erreur réseau/timeout). */
  public record CheckResult(boolean ok, Integer httpStatus, String reason) {
    static CheckResult success(int status) {
      return new CheckResult(true, status, null);
    }
  }

  public CheckResult check(String url, String expectFragment) {
    if (url == null || url.isBlank() || expectFragment == null || expectFragment.isBlank()) {
      return new CheckResult(false, null, "URL ou fragment attendu manquant");
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .header("User-Agent", USER_AGENT)
              .timeout(TIMEOUT)
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        return new CheckResult(false, status, "HTTP " + status);
      }
      if (!normalize(response.body()).contains(normalize(expectFragment))) {
        return new CheckResult(
            false, status, "200 mais fragment attendu absent : \"" + expectFragment + "\"");
      }
      return CheckResult.success(status);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new CheckResult(false, null, "interrupted");
    } catch (Exception e) {
      return new CheckResult(false, null, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  /** Même normalisation que scripts/check-sources.mjs. */
  static String normalize(String html) {
    return html.replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace('’', '\'')
        .replace('‘', '\'')
        .toLowerCase(Locale.ROOT);
  }
}

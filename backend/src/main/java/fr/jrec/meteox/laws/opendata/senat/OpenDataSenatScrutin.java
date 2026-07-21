package fr.jrec.meteox.laws.opendata.senat;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Télécharge le JSON d'un scrutin public du Sénat ({@code scr{session}-{n}.json}). Les scrutins
 * publiés sont immuables : GET conditionnel ETag, cache disque réutilisé en cas d'indisponibilité.
 * Un scrutin absent (HTTP 404) rend {@link Optional#empty()}.
 */
@ApplicationScoped
public class OpenDataSenatScrutin {

  private static final Logger LOG = Logger.getLogger(OpenDataSenatScrutin.class);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

  @ConfigProperty(
      name = "meteox.opendata.senat.scrutin.url-template",
      defaultValue = "https://www.senat.fr/scrutin-public/%d/scr%d-%d.json")
  String urlTemplate;

  @ConfigProperty(name = "meteox.opendata.dir", defaultValue = "target/opendata")
  String dataDir;

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(HTTP_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  public Optional<byte[]> scrutinJson(int session, int numero)
      throws IOException, InterruptedException {
    Path dir = Path.of(dataDir);
    Files.createDirectories(dir);
    Path file = dir.resolve("senat-scr-" + session + "-" + numero + ".json");
    Path etagFile = dir.resolve("senat-scr-" + session + "-" + numero + ".etag");

    String url = String.format(urlTemplate, session, session, numero);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET();
    if (Files.exists(file) && Files.exists(etagFile)) {
      request.header("If-None-Match", Files.readString(etagFile).trim());
    }

    Path tmp = Files.createTempFile(dir, "senat-scr-", ".json.part");
    try {
      HttpResponse<Path> response;
      try {
        response = client.send(request.build(), HttpResponse.BodyHandlers.ofFile(tmp));
      } catch (IOException networkError) {
        if (Files.exists(file)) {
          LOG.warnf(
              "Scrutin Sénat %d-%d indisponible (%s) — réutilisation du cache",
              session, numero, networkError.getMessage());
          return Optional.of(Files.readAllBytes(file));
        }
        throw networkError;
      }
      if (response.statusCode() == 304) {
        return Optional.of(Files.readAllBytes(file));
      }
      if (response.statusCode() == 200) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        response
            .headers()
            .firstValue("ETag")
            .ifPresent(
                etag -> {
                  try {
                    Files.writeString(etagFile, etag);
                  } catch (IOException e) {
                    LOG.warnf("ETag non persisté pour le scrutin Sénat %d-%d", session, numero);
                  }
                });
        return Optional.of(Files.readAllBytes(file));
      }
      if (response.statusCode() == 404) {
        return Optional.empty();
      }
      if (Files.exists(file)) {
        LOG.warnf(
            "Scrutin Sénat %d-%d → HTTP %d — réutilisation du cache",
            session, numero, response.statusCode());
        return Optional.of(Files.readAllBytes(file));
      }
      throw new IOException("Scrutin Sénat " + session + "-" + numero + " → HTTP " + response.statusCode());
    } finally {
      Files.deleteIfExists(tmp);
    }
  }
}

package fr.jrec.meteox.laws.opendata.senat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Accès au jeu open data {@code ODSEN_HISTOGROUPES} du Sénat (matricule → groupe, avec historique
 * daté, ~1,5 Mo). GET conditionnel ETag, cache disque réutilisé si indisponible. L'index {@link
 * HistoGroupes} est mémoïsé et re-parsé seulement quand le fichier change.
 */
@ApplicationScoped
public class OpenDataHistoGroupes {

  private static final Logger LOG = Logger.getLogger(OpenDataHistoGroupes.class);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);

  @ConfigProperty(
      name = "meteox.opendata.senat.histogroupes.url-template",
      defaultValue = "https://data.senat.fr/data/senateurs/ODSEN_HISTOGROUPES.json")
  String url;

  @ConfigProperty(name = "meteox.opendata.dir", defaultValue = "target/opendata")
  String dataDir;

  @ConfigProperty(name = "meteox.opendata.refresh-seconds", defaultValue = "3600")
  long refreshSeconds;

  @Inject ObjectMapper mapper;

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(HTTP_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  private volatile HistoGroupes cached;
  private volatile Instant lastChecked;

  public synchronized HistoGroupes index() throws IOException, InterruptedException {
    Instant now = Instant.now();
    if (cached != null
        && lastChecked != null
        && lastChecked.plusSeconds(refreshSeconds).isAfter(now)) {
      return cached;
    }
    boolean changed = ensureFile();
    if (cached == null || changed) {
      cached = HistoGroupes.parse(mapper.readTree(filePath().toFile()));
      LOG.infof("ODSEN_HISTOGROUPES parsé : %d sénateur(s) indexé(s)", cached.size());
    }
    lastChecked = Instant.now();
    return cached;
  }

  private Path filePath() {
    return Path.of(dataDir).resolve("ODSEN_HISTOGROUPES.json");
  }

  private boolean ensureFile() throws IOException, InterruptedException {
    Path dir = Path.of(dataDir);
    Files.createDirectories(dir);
    Path file = filePath();
    Path etagFile = dir.resolve("ODSEN_HISTOGROUPES.etag");

    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET();
    if (Files.exists(file) && Files.exists(etagFile)) {
      request.header("If-None-Match", Files.readString(etagFile).trim());
    }

    Path tmp = Files.createTempFile(dir, "histogroupes-", ".json.part");
    try {
      HttpResponse<Path> response;
      try {
        response = client.send(request.build(), HttpResponse.BodyHandlers.ofFile(tmp));
      } catch (IOException networkError) {
        return reuseCacheOrThrow(file, networkError);
      }
      if (response.statusCode() == 304) {
        return false;
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
                    LOG.warn("ETag ODSEN_HISTOGROUPES non persisté");
                  }
                });
        LOG.infof("ODSEN_HISTOGROUPES téléchargé (%d octets)", Files.size(file));
        return true;
      }
      return reuseCacheOrThrow(
          file, new IOException("Téléchargement ODSEN_HISTOGROUPES → HTTP " + response.statusCode()));
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  private static boolean reuseCacheOrThrow(Path file, IOException cause) throws IOException {
    if (Files.exists(file)) {
      LOG.warnf("ODSEN_HISTOGROUPES indisponible (%s) — réutilisation du cache", cause.getMessage());
      return false;
    }
    throw cause;
  }
}

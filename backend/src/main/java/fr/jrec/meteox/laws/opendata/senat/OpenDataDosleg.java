package fr.jrec.meteox.laws.opendata.senat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Accès au jeu open data « Dosleg » du Sénat ({@code dosleg.zip}, ~16 Mo → SQL ~126 Mo, quotidien).
 * Même stratégie que les jeux AN ({@code OpenDataScrutins}/{@code OpenDataDossiers}) : GET
 * conditionnel via ETag, cache disque réutilisé si le Sénat est indisponible (les dossiers/scrutins
 * passés sont immuables). Le dump n'est re-parsé que lorsqu'il a changé ; la vue {@link
 * DoslegDataset} est mémoïsée le temps d'une fenêtre de fraîcheur.
 */
@ApplicationScoped
public class OpenDataDosleg {

  private static final Logger LOG = Logger.getLogger(OpenDataDosleg.class);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(120);

  @ConfigProperty(
      name = "meteox.opendata.senat.dosleg.url-template",
      defaultValue = "https://data.senat.fr/data/dosleg/dosleg.zip")
  String url;

  @ConfigProperty(name = "meteox.opendata.dir", defaultValue = "target/opendata")
  String dataDir;

  @ConfigProperty(name = "meteox.opendata.refresh-seconds", defaultValue = "3600")
  long refreshSeconds;

  @Inject DoslegParser parser;

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(HTTP_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  private volatile DoslegDataset cached;
  private volatile Instant lastChecked;

  /** Vue Dosleg à jour (re-téléchargée/re-parsée seulement si nécessaire). */
  public synchronized DoslegDataset dataset() throws IOException, InterruptedException {
    Instant now = Instant.now();
    if (cached != null
        && lastChecked != null
        && lastChecked.plusSeconds(refreshSeconds).isAfter(now)) {
      return cached;
    }
    boolean changed = ensureZip();
    if (cached == null || changed) {
      cached = parseZip(zipPath());
      LOG.infof("Dump Dosleg parsé : %d loi(s) reliée(s) à l'AN", cached.loiCount());
    }
    lastChecked = Instant.now();
    return cached;
  }

  private Path zipPath() {
    return Path.of(dataDir).resolve("dosleg.zip");
  }

  /** Télécharge (ou revalide via ETag) {@code dosleg.zip}. Rend {@code true} s'il a (re)téléchargé. */
  private boolean ensureZip() throws IOException, InterruptedException {
    Path dir = Path.of(dataDir);
    Files.createDirectories(dir);
    Path zip = zipPath();
    Path etagFile = dir.resolve("dosleg.etag");

    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET();
    if (Files.exists(zip) && Files.exists(etagFile)) {
      request.header("If-None-Match", Files.readString(etagFile).trim());
    }

    Path tmp = Files.createTempFile(dir, "dosleg-", ".zip.part");
    try {
      HttpResponse<Path> response;
      try {
        response = client.send(request.build(), HttpResponse.BodyHandlers.ofFile(tmp));
      } catch (IOException networkError) {
        return reuseCacheOrThrow(zip, networkError);
      }
      if (response.statusCode() == 304) {
        LOG.debug("Dump Dosleg inchangé (ETag)");
        return false;
      }
      if (response.statusCode() == 200) {
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
        response
            .headers()
            .firstValue("ETag")
            .ifPresent(
                etag -> {
                  try {
                    Files.writeString(etagFile, etag);
                  } catch (IOException e) {
                    LOG.warn("ETag Dosleg non persisté");
                  }
                });
        LOG.infof("Dump Dosleg téléchargé (%d octets)", Files.size(zip));
        return true;
      }
      return reuseCacheOrThrow(
          zip, new IOException("Téléchargement dosleg.zip → HTTP " + response.statusCode()));
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  /**
   * Le Dosleg passé est immuable : en cas d'indisponibilité réseau/HTTP, le cache disque reste une
   * source valable. Rend {@code false} (rien de neuf) si le cache existe, sinon relaie l'échec.
   */
  private static boolean reuseCacheOrThrow(Path zip, IOException cause) throws IOException {
    if (Files.exists(zip)) {
      LOG.warnf("Open data Sénat indisponible (%s) — réutilisation du dosleg.zip en cache", cause.getMessage());
      return false;
    }
    throw cause;
  }

  private DoslegDataset parseZip(Path zip) throws IOException {
    try (ZipFile zf = new ZipFile(zip.toFile())) {
      ZipEntry sqlEntry = null;
      for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
        ZipEntry entry = e.nextElement();
        if (!entry.isDirectory() && entry.getName().endsWith(".sql")) {
          sqlEntry = entry;
          break;
        }
      }
      if (sqlEntry == null) {
        throw new IOException("Aucune entrée .sql dans dosleg.zip");
      }
      try (InputStream in = zf.getInputStream(sqlEntry)) {
        return parser.parse(in);
      }
    }
  }
}

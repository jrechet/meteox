package fr.jrec.meteox.laws.opendata;

import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Accès au jeu open data « Scrutins » de l'Assemblée nationale (un zip JSON par législature,
 * ~26 Mo, mis à jour quotidiennement). Téléchargement en GET conditionnel (ETag) : le zip
 * n'est re-téléchargé que s'il a changé côté AN ; en cas d'indisponibilité réseau, le dernier
 * zip en cache est réutilisé (les scrutins passés sont immuables).
 */
@ApplicationScoped
public class OpenDataScrutins {

  private static final Logger LOG = Logger.getLogger(OpenDataScrutins.class);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);

  @ConfigProperty(
      name = "meteox.opendata.scrutins.url-template",
      defaultValue =
          "https://data.assemblee-nationale.fr/static/openData/repository/%d/loi/scrutins/Scrutins.json.zip")
  String urlTemplate;

  @ConfigProperty(name = "meteox.opendata.dir", defaultValue = "target/opendata")
  String dataDir;

  /** Fraîcheur : au sein d'une même fenêtre, on ne revérifie pas le remote (0 = toujours). */
  @ConfigProperty(name = "meteox.opendata.refresh-seconds", defaultValue = "3600")
  long refreshSeconds;

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(HTTP_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  private final Map<Integer, Instant> lastChecked = new ConcurrentHashMap<>();

  /**
   * Contenu JSON du scrutin demandé, extrait du zip de sa législature.
   *
   * @throws IOException si le jeu de données est indisponible (ni remote ni cache)
   */
  public Optional<byte[]> scrutinJson(ScrutinRef ref) throws IOException, InterruptedException {
    Path zip = ensureDataset(ref.legislature());
    try (ZipFile zf = new ZipFile(zip.toFile())) {
      ZipEntry entry = findEntry(zf, ref.fileName());
      if (entry == null) {
        return Optional.empty();
      }
      try (InputStream in = zf.getInputStream(entry)) {
        return Optional.of(in.readAllBytes());
      }
    }
  }

  /** Télécharge (ou revalide via ETag) le zip de la législature ; rend le chemin local. */
  Path ensureDataset(int legislature) throws IOException, InterruptedException {
    Path dir = Path.of(dataDir);
    Files.createDirectories(dir);
    Path zip = dir.resolve(legislature + ".zip");
    Path etagFile = dir.resolve(legislature + ".etag");

    Instant checked = lastChecked.get(legislature);
    boolean fresh =
        checked != null && checked.plusSeconds(refreshSeconds).isAfter(Instant.now());
    if (fresh && Files.exists(zip)) {
      return zip;
    }

    String url = String.format(urlTemplate, legislature);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET();
    if (Files.exists(zip) && Files.exists(etagFile)) {
      request.header("If-None-Match", Files.readString(etagFile).trim());
    }

    try {
      HttpResponse<byte[]> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() == 304) {
        LOG.debugf("Jeu Scrutins %de législature inchangé (ETag)", legislature);
      } else if (response.statusCode() == 200) {
        Path tmp = Files.createTempFile(dir, legislature + "-", ".zip.part");
        Files.write(tmp, response.body());
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
        response
            .headers()
            .firstValue("ETag")
            .ifPresent(
                etag -> {
                  try {
                    Files.writeString(etagFile, etag);
                  } catch (IOException e) {
                    LOG.warnf("ETag non persisté pour la législature %d", legislature);
                  }
                });
        LOG.infof(
            "Jeu Scrutins %de législature téléchargé (%d octets)",
            legislature, response.body().length);
      } else {
        throw new IOException(
            "Téléchargement du jeu Scrutins (lég. " + legislature + ") → HTTP "
                + response.statusCode());
      }
    } catch (IOException e) {
      if (Files.exists(zip)) {
        // Les scrutins passés sont immuables : le cache reste une source valable.
        LOG.warnf(
            "Open data AN indisponible (%s) — réutilisation du zip en cache pour la lég. %d",
            e.getMessage(), legislature);
        return zip;
      }
      throw e;
    }
    lastChecked.put(legislature, Instant.now());
    return zip;
  }

  /** Les entrées du zip AN portent un préfixe de dossier (ex. json/VTANR5L17V844.json). */
  private static ZipEntry findEntry(ZipFile zf, String fileName) {
    for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
      ZipEntry entry = e.nextElement();
      String name = entry.getName();
      if (!entry.isDirectory() && (name.equals(fileName) || name.endsWith("/" + fileName))) {
        return entry;
      }
    }
    return null;
  }
}

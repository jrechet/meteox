package fr.jrec.meteox.laws.opendata;

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
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Accès au jeu open data « dossiers législatifs » de l'AN (un zip JSON par législature,
 * ~10 Mo). Même stratégie que {@link OpenDataScrutins} : GET conditionnel ETag, cache disque
 * réutilisé si l'AN est indisponible. Ici on itère TOUS les dossiers du zip (nœud
 * {@code dossierParlementaire}) plutôt que d'en extraire un seul.
 */
@ApplicationScoped
public class OpenDataDossiers {

  private static final Logger LOG = Logger.getLogger(OpenDataDossiers.class);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(90);

  @ConfigProperty(
      name = "meteox.opendata.dossiers.url-template",
      defaultValue =
          "https://data.assemblee-nationale.fr/static/openData/repository/%d/loi/dossiers_legislatifs/Dossiers_Legislatifs.json.zip")
  String urlTemplate;

  @ConfigProperty(name = "meteox.opendata.dir", defaultValue = "target/opendata")
  String dataDir;

  @Inject DossierParser parser;

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(HTTP_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  /**
   * Parse chaque dossier de la législature et le transmet au consommateur. Un dossier
   * illisible est ignoré (loggé) sans interrompre le parcours du reste du jeu.
   */
  public void forEachDossier(int legislature, Consumer<DossierParser.ParsedDossier> consumer)
      throws IOException, InterruptedException {
    Path zip = ensureDataset(legislature);
    int failures = 0;
    try (ZipFile zf = new ZipFile(zip.toFile())) {
      for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
        ZipEntry entry = e.nextElement();
        if (entry.isDirectory() || !entry.getName().contains("dossierParlementaire/")) {
          continue;
        }
        try (InputStream in = zf.getInputStream(entry)) {
          consumer.accept(parser.parse(in));
        } catch (RuntimeException ex) {
          failures++;
        }
      }
    }
    if (failures > 0) {
      LOG.warnf("%d dossier(s) ignoré(s) (parsing) sur la législature %d", failures, legislature);
    }
  }

  /** Télécharge (ou revalide via ETag) le zip de la législature ; rend le chemin local. */
  Path ensureDataset(int legislature) throws IOException, InterruptedException {
    Path dir = Path.of(dataDir);
    Files.createDirectories(dir);
    Path zip = dir.resolve("dossiers-" + legislature + ".zip");
    Path etagFile = dir.resolve("dossiers-" + legislature + ".etag");

    String url = String.format(urlTemplate, legislature);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET();
    if (Files.exists(zip) && Files.exists(etagFile)) {
      request.header("If-None-Match", Files.readString(etagFile).trim());
    }

    Path tmp = null;
    try {
      tmp = Files.createTempFile(dir, "dossiers-" + legislature + "-", ".zip.part");
      HttpResponse<Path> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofFile(tmp));
      if (response.statusCode() == 304) {
        LOG.debugf("Jeu dossiers %de législature inchangé (ETag)", legislature);
      } else if (response.statusCode() == 200) {
        try {
          Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
          response
              .headers()
              .firstValue("ETag")
              .ifPresent(etag -> writeQuietly(etagFile, etag, legislature));
          LOG.infof("Jeu dossiers %de législature téléchargé", legislature);
        } catch (IOException diskError) {
          throw new IOException("Écriture locale du jeu dossiers impossible : " + diskError.getMessage(), diskError);
        }
      } else {
        throw new IOException(
            "Téléchargement du jeu dossiers (lég. " + legislature + ") → HTTP " + response.statusCode());
      }
    } catch (IOException networkError) {
      if (Files.exists(zip)) {
        LOG.warnf(
            "Open data AN dossiers indisponible (%s) — réutilisation du cache pour la lég. %d",
            networkError.getMessage(), legislature);
        return zip;
      }
      throw networkError;
    } finally {
      if (tmp != null) {
        Files.deleteIfExists(tmp);
      }
    }
    return zip;
  }

  private static void writeQuietly(Path etagFile, String etag, int legislature) {
    try {
      Files.writeString(etagFile, etag);
    } catch (IOException e) {
      LOG.warnf("ETag dossiers non persisté pour la législature %d", legislature);
    }
  }
}

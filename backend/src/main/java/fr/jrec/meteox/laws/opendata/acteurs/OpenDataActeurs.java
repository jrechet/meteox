package fr.jrec.meteox.laws.opendata.acteurs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.jrec.meteox.laws.opendata.acteurs.ActeurParser.ParsedActeur;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Accès au jeu open data « AMO30 » de l'AN (tous les acteurs, mandats et organes, un zip JSON
 * ~volumineux). Même stratégie que {@code OpenDataScrutins} : GET conditionnel (ETag), cache
 * disque streamé (jamais bufferisé en tas), réutilisé si l'AN est indisponible (le référentiel
 * des acteurs varie peu). On itère les entrées {@code json/acteur/*.json} (nœud {@code acteur})
 * et {@code json/organe/*.json} (nœud {@code organe}) plutôt que d'en extraire une seule.
 */
@ApplicationScoped
public class OpenDataActeurs {

  private static final Logger LOG = Logger.getLogger(OpenDataActeurs.class);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(120);

  @ConfigProperty(
      name = "meteox.opendata.acteurs.url-template",
      defaultValue =
          "https://data.assemblee-nationale.fr/static/openData/repository/%d/amo/tous_acteurs_mandats_organes_xi_legislature/AMO30_tous_acteurs_tous_mandats_tous_organes_historique.json.zip")
  String urlTemplate;

  @ConfigProperty(name = "meteox.opendata.dir", defaultValue = "target/opendata")
  String dataDir;

  /** Fraîcheur : au sein d'une même fenêtre, on ne revérifie pas le remote (0 = toujours). */
  @ConfigProperty(name = "meteox.opendata.refresh-seconds", defaultValue = "3600")
  long refreshSeconds;

  @Inject ActeurParser acteurParser;

  private final ObjectMapper mapper = new ObjectMapper();

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(HTTP_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  private final Map<Integer, Instant> lastChecked = new ConcurrentHashMap<>();

  /** Organe parsé (données brutes) : seul le sigle des groupes politiques (GP) nous intéresse. */
  public record ParsedOrgane(String uid, String sigle, String libelle, String codeType) {
    public boolean isGroupePolitique() {
      return "GP".equals(codeType);
    }
  }

  /**
   * Parse chaque entrée du jeu et la transmet au consommateur adéquat : les acteurs à
   * {@code onActeur} (via {@link ActeurParser}), les organes à {@code onOrgane}. Une entrée
   * illisible est ignorée (loggée) sans interrompre le parcours du reste du zip.
   */
  public void forEachEntry(
      int legislature, Consumer<ParsedActeur> onActeur, Consumer<ParsedOrgane> onOrgane)
      throws IOException, InterruptedException {
    Path zip = ensureDataset(legislature);
    int failures = 0;
    try (ZipFile zf = new ZipFile(zip.toFile())) {
      for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
        ZipEntry entry = e.nextElement();
        String name = entry.getName();
        if (entry.isDirectory() || !name.endsWith(".json")) {
          continue;
        }
        try (InputStream in = zf.getInputStream(entry)) {
          if (name.contains("/acteur/")) {
            onActeur.accept(acteurParser.parse(in));
          } else if (name.contains("/organe/")) {
            onOrgane.accept(parseOrgane(in));
          }
        } catch (RuntimeException ex) {
          failures++;
        }
      }
    }
    if (failures > 0) {
      LOG.warnf("%d entrée(s) AMO30 ignorée(s) (parsing) sur la législature %d", failures, legislature);
    }
  }

  private ParsedOrgane parseOrgane(InputStream json) throws IOException {
    JsonNode o = mapper.readTree(json).path("organe");
    if (o.isMissingNode()) {
      throw new IllegalArgumentException("JSON d'organe invalide : nœud 'organe' absent");
    }
    return new ParsedOrgane(
        o.path("uid").asText(""),
        o.path("libelleAbrev").asText(""),
        o.path("libelle").asText(""),
        o.path("codeType").asText(""));
  }

  /** Télécharge (ou revalide via ETag) le zip de la législature ; rend le chemin local. */
  Path ensureDataset(int legislature) throws IOException, InterruptedException {
    Path dir = Path.of(dataDir);
    Files.createDirectories(dir);
    Path zip = dir.resolve("acteurs-" + legislature + ".zip");
    Path etagFile = dir.resolve("acteurs-" + legislature + ".etag");

    Instant checked = lastChecked.get(legislature);
    boolean fresh = checked != null && checked.plusSeconds(refreshSeconds).isAfter(Instant.now());
    if (fresh && Files.exists(zip)) {
      return zip;
    }

    String url = String.format(urlTemplate, legislature);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET();
    if (Files.exists(zip) && Files.exists(etagFile)) {
      request.header("If-None-Match", Files.readString(etagFile).trim());
    }

    // Le zip est volumineux : on le streame directement sur disque, jamais bufferisé en tas.
    Path tmp = Files.createTempFile(dir, "acteurs-" + legislature + "-", ".zip.part");
    try {
      HttpResponse<Path> response;
      try {
        response = client.send(request.build(), HttpResponse.BodyHandlers.ofFile(tmp));
      } catch (IOException networkError) {
        return reuseCacheOrThrow(zip, legislature, networkError);
      }

      if (response.statusCode() == 304) {
        LOG.debugf("Jeu AMO30 %de législature inchangé (ETag)", legislature);
      } else if (response.statusCode() == 200) {
        try {
          Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
          response
              .headers()
              .firstValue("ETag")
              .ifPresent(etag -> writeQuietly(etagFile, etag, legislature));
          LOG.infof("Jeu AMO30 %de législature téléchargé (%d octets)", legislature, Files.size(zip));
        } catch (IOException diskError) {
          // Échec local (déplacement, lecture taille…) : pas une indisponibilité réseau.
          LOG.errorf(diskError, "Échec local d'écriture du jeu AMO30 (lég. %d)", legislature);
          throw diskError;
        }
      } else {
        return reuseCacheOrThrow(
            zip,
            legislature,
            new IOException(
                "Téléchargement du jeu AMO30 (lég. " + legislature + ") → HTTP "
                    + response.statusCode()));
      }
    } finally {
      // Le .part ne doit jamais traîner : déplacé (no-op) ou abandonné (304, HTTP erreur, exception).
      Files.deleteIfExists(tmp);
    }
    lastChecked.put(legislature, Instant.now());
    return zip;
  }

  /**
   * Le référentiel des acteurs varie peu : en cas d'indisponibilité réseau/HTTP, le cache disque
   * reste une source valable. Sans cache, l'échec remonte tel quel.
   */
  private static Path reuseCacheOrThrow(Path zip, int legislature, IOException cause)
      throws IOException {
    if (Files.exists(zip)) {
      LOG.warnf(
          "Open data AN indisponible (%s) — réutilisation du zip AMO30 en cache pour la lég. %d",
          cause.getMessage(), legislature);
      return zip;
    }
    throw cause;
  }

  private static void writeQuietly(Path etagFile, String etag, int legislature) {
    try {
      Files.writeString(etagFile, etag);
    } catch (IOException e) {
      LOG.warnf("ETag AMO30 non persisté pour la législature %d", legislature);
    }
  }
}

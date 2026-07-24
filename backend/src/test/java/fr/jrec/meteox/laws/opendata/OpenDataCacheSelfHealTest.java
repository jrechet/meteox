package fr.jrec.meteox.laws.opendata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.opendata.acteurs.OpenDataActeurs;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RÉGRESSION (incident prod 2026-07) : un zip TRONQUÉ en cache était scellé pour toujours par
 * l'ETag — l'AN répond 304 « inchangé », le cache corrompu n'est jamais re-téléchargé, et toute
 * la résolution des signataires échoue en boucle (« zip END header not found »). Chaque cache
 * open data doit s'AUTO-GUÉRIR : zip illisible → invalider (fichier + ETag) → re-télécharger une
 * fois. Scénario reproduit ici à l'identique : poison en cache + ETag stocké + serveur qui
 * répond 304 à cet ETag et 200 (zip valide) sans lui.
 */
@QuarkusTest
class OpenDataCacheSelfHealTest {

  private static final int WIREMOCK_PORT = 18089;
  private static final Path CACHE = Path.of("target", "test-opendata");
  private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");
  private static final byte[] POISON = "pas un zip du tout".getBytes(StandardCharsets.UTF_8);
  private static WireMockServer wireMock;

  @Inject OpenDataActeurs acteurs;
  @Inject OpenDataDossiers dossiers;
  @Inject OpenDataScrutins scrutins;

  @BeforeAll
  static void start() {
    wireMock = new WireMockServer(WireMockConfiguration.options().port(WIREMOCK_PORT));
    wireMock.start();
  }

  @AfterAll
  static void stop() {
    wireMock.stop();
  }

  @BeforeEach
  void reset() throws Exception {
    wireMock.resetAll();
    Files.createDirectories(CACHE);
  }

  /** Empoisonne le cache : zip corrompu + ETag « poison » que le serveur validera par 304. */
  private void poison(String baseName) throws Exception {
    Files.write(CACHE.resolve(baseName + ".zip"), POISON);
    Files.writeString(CACHE.resolve(baseName + ".etag"), "\"poison\"");
  }

  /** 304 pour l'ETag empoisonné (la pathologie), 200 + zip valide sinon (la guérison). */
  private void stubPoisonedThenFresh(String url, byte[] freshZip) {
    wireMock.stubFor(
        get(urlEqualTo(url))
            .withHeader("If-None-Match", equalTo("\"poison\""))
            .willReturn(aResponse().withStatus(304)));
    wireMock.stubFor(
        get(urlEqualTo(url))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/zip")
                    .withHeader("ETag", "\"fresh\"")
                    .withBody(freshZip)));
  }

  private static byte[] zipOf(String entryName, byte[] content) throws Exception {
    var out = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      zos.putNextEntry(new ZipEntry(entryName));
      zos.write(content);
      zos.closeEntry();
    }
    return out.toByteArray();
  }

  @Test
  void cache_acteurs_corrompu_scelle_par_etag_sauto_guerit() throws Exception {
    poison("acteurs-17");
    String organe =
        "{\"organe\":{\"uid\":\"PO800000\",\"libelleAbrev\":\"TEST\",\"libelle\":\"Groupe test\",\"codeType\":\"GP\"}}";
    stubPoisonedThenFresh(
        "/opendata/17/Acteurs.json.zip",
        zipOf("json/organe/PO800000.json", organe.getBytes(StandardCharsets.UTF_8)));

    var organes = new ArrayList<OpenDataActeurs.ParsedOrgane>();
    acteurs.forEachEntry(17, a -> {}, organes::add);

    assertEquals(1, organes.size(), "après auto-guérison, le zip frais doit être lu");
    assertEquals("TEST", organes.get(0).sigle());
    // Le cache est réparé : le fichier n'est plus le poison.
    assertTrue(Files.size(CACHE.resolve("acteurs-17.zip")) != POISON.length);
  }

  @Test
  void cache_dossiers_corrompu_scelle_par_etag_sauto_guerit() throws Exception {
    poison("dossiers-17");
    byte[] dossier = Files.readAllBytes(FIXTURES.resolve("dossiers/DLR5L17N53637.json"));
    stubPoisonedThenFresh(
        "/opendata/17/Dossiers.json.zip",
        zipOf("json/dossierParlementaire/DLR5L17N53637.json", dossier));

    var vus = new ArrayList<DossierParser.ParsedDossier>();
    dossiers.forEachDossier(17, vus::add);

    assertEquals(1, vus.size(), "après auto-guérison, le zip frais doit être lu");
    assertEquals("DLR5L17N53637", vus.get(0).uid());
  }

  @Test
  void cache_scrutins_corrompu_scelle_par_etag_sauto_guerit() throws Exception {
    poison("17");
    byte[] scrutin = Files.readAllBytes(FIXTURES.resolve("scrutins/VTANR5L17V844.json"));
    stubPoisonedThenFresh(
        "/opendata/17/Scrutins.json.zip", zipOf("json/VTANR5L17V844.json", scrutin));

    var json = scrutins.scrutinJson(new ScrutinRef(17, 844));

    assertTrue(json.isPresent(), "après auto-guérison, le scrutin doit être extrait du zip frais");
  }
}

package fr.jrec.meteox.laws.opendata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.Signataire;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Fiabilisation issue #58 — vrai chemin de {@link SignataireResolver#resolve} pour un dépôt hérité
 * d'une législature antérieure. Le document {@code …L16B…} n'est servi QUE dans le zip de la 16e ;
 * une résolution réussie depuis un dossier de la 17e prouve que le document a été cherché dans le
 * zip de SA propre législature (l'ancien code interrogeait le zip de la 17e → introuvable → vide).
 */
@QuarkusTest
class SignataireResolverIntegrationTest {

  private static final int WIREMOCK_PORT = 18089; // aligné sur %test.meteox.opendata.*
  private static final String DEPOT_L16 = "PIONANR5L16B0001";
  private static final String AUTEUR_REF = "PA999999"; // volontairement absent de tout référentiel
  private static final String COSIGN_REF = "PA888888";
  private static WireMockServer wireMock;

  @Inject SignataireResolver resolver;

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
    Path cache = Path.of("target/test-opendata");
    if (Files.exists(cache)) {
      try (var walk = Files.walk(cache)) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
      }
    }
    // AMO30 vide mais VALIDE : le référentiel se construit sans lever (noms/groupes non résolus,
    // ce qui n'importe pas ici — on vérifie que le DOCUMENT est bien retrouvé et parsé).
    stub("/opendata/17/Acteurs.json.zip", "amo-17", amoZipWithoutActors());
  }

  @Test
  void resout_un_depot_herite_d_une_legislature_anterieure() throws Exception {
    // Le document n'existe QUE dans le zip de la 16e ; celui de la 17e ne le contient pas.
    stub("/opendata/16/Dossiers.json.zip", "dos-16", zipWithDocument(DEPOT_L16, documentJson()));

    Optional<List<Signataire>> resolved = resolver.resolve(17, DEPOT_L16);

    assertTrue(resolved.isPresent(), "le document doit être retrouvé dans le zip de la 16e législature");
    List<Signataire> sigs = resolved.get();
    assertFalse(sigs.isEmpty(), "auteur + cosignataire attendus");
    assertEquals("auteur", sigs.get(0).role());
    assertEquals(AUTEUR_REF, sigs.get(0).acteurRef());
    assertTrue(
        sigs.stream().anyMatch(s -> "cosignataire".equals(s.role()) && COSIGN_REF.equals(s.acteurRef())),
        "le cosignataire du document doit être présent");
  }

  @Test
  void un_depot_dont_la_legislature_est_absente_preserve_l_existant_sans_lever() {
    // Aucune stub pour /opendata/15/Dossiers.json.zip → le fetch échoue (404).
    // Contrat anti-perte : la résolution rend Optional.empty (l'appelant préserve l'existant),
    // jamais d'exception, jamais une liste vide qui écraserait des signataires stockés.
    Optional<List<Signataire>> resolved = resolver.resolve(17, "PIONANR5L15B0001");

    assertTrue(resolved.isEmpty(), "législature introuvable → « je ne sais pas », existant préservé");
  }

  // --- helpers ---

  private static void stub(String url, String etag, byte[] body) {
    wireMock.stubFor(
        get(urlEqualTo(url))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/zip")
                    .withHeader("ETag", "\"" + etag + "\"")
                    .withBody(body)));
  }

  private static String documentJson() {
    return "{\"document\":{\"auteurs\":{\"auteur\":{\"acteur\":{\"acteurRef\":\"" + AUTEUR_REF + "\"}}},"
        + "\"coSignataires\":{\"coSignataire\":[{\"acteur\":{\"acteurRef\":\"" + COSIGN_REF + "\"}}]}}}";
  }

  private static byte[] zipWithDocument(String documentUid, String json) throws Exception {
    var out = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      zos.putNextEntry(new ZipEntry("json/document/" + documentUid + ".json"));
      zos.write(json.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return out.toByteArray();
  }

  /** Zip AMO30 valide SANS aucune entrée acteur/organe → référentiel construit mais vide. */
  private static byte[] amoZipWithoutActors() throws Exception {
    var out = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      zos.putNextEntry(new ZipEntry("json/.keep")); // marqueur non-.json (zip valide, ignoré au scan)
      zos.closeEntry();
    }
    return out.toByteArray();
  }
}

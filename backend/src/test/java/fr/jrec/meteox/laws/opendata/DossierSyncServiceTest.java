package fr.jrec.meteox.laws.opendata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.repository.LawRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Acceptance issue #3 (tâche 2, sync-dossiers) : les dossiers open data (fixtures réelles) sont
 * servis par WireMock. On vérifie : détection thématique (candidat de staging, jamais publié),
 * exclusion des dossiers hors thème, et la garantie « une loi promulguée ne reste jamais à
 * venir » (dépublication d'une carte upcoming dont le dossier devient promulgué).
 */
@QuarkusTest
class DossierSyncServiceTest {

  private static final int WIREMOCK_PORT = 18089;
  private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures", "dossiers");
  // eau, en cours (candidat attendu) ; Ratification, hors thème ET promulguée (exclue + démote).
  private static final String EAU = "DLR5L17N53637";
  private static final String PROMULGUEE = "DLR5L17N51352";
  private static WireMockServer wireMock;

  @Inject DossierSyncService service;
  @Inject DossierRepository candidates;
  @Inject LawRepository laws;
  @Inject DataSource dataSource;

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
  }

  @AfterEach
  void cleanup() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM dossier_candidates");
      st.executeUpdate("DELETE FROM scrutins WHERE law_id LIKE 'test-%'");
      // 'test-%' + les lois upcoming promues (id = uid dossier, ex. DLR…) créées par les tests.
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'test-%' OR id LIKE 'DLR%'");
    }
  }

  @Test
  void detects_themed_dossiers_as_staging_candidates_and_excludes_off_theme() throws Exception {
    stubDataset(17, zipOf(EAU, PROMULGUEE));

    var report = service.syncAll();

    // 2 dossiers scannés, 1 seul thématique (l'eau) ; la ratification hors thème est ignorée.
    assertEquals(2, report.scanned());
    assertEquals(1, report.candidates());
    var staged = candidates.listByStatus("candidate");
    assertEquals(1, staged.size());
    assertEquals(EAU, staged.get(0).uid());
    assertTrue(staged.get(0).theme().startsWith("eau"));
    assertTrue(
        staged.get(0).dossierUrl().endsWith("/dyn/17/dossiers/" + EAU),
        "URL officielle du dossier attendue");

    // Golden Rule : rien n'est publié — la section reste vide (les 4 lois votées seules exposées).
    when().get("/api/laws").then().statusCode(200).body("status", not(hasItem("upcoming")));
  }

  @Test
  void promulgated_dossier_demotes_a_promoted_upcoming_card() throws Exception {
    // Une carte upcoming a été promue depuis le dossier PROMULGUEE (avant sa promulgation).
    insertUpcomingLaw("test-upcoming", "https://www.assemblee-nationale.fr/dyn/17/dossiers/" + PROMULGUEE);
    insertPromotedCandidate(PROMULGUEE, "test-upcoming");
    stubDataset(17, zipOf(PROMULGUEE));

    var report = service.syncAll();

    // Le dossier est promulgué → la carte upcoming est dépubliée (jamais « à venir »).
    assertEquals(1, report.demoted());
    assertFalse(laws.findById("test-upcoming").orElseThrow().published());
    when().get("/api/laws").then().statusCode(200).body("id", not(hasItem("test-upcoming")));
    assertTrue(candidates.findByUid(PROMULGUEE).orElseThrow().terminated());
  }

  @Test
  void rescanning_is_idempotent_no_duplicate_candidates() throws Exception {
    stubDataset(17, zipOf(EAU));
    service.syncAll();
    service.syncAll();
    assertEquals(1, candidates.listByStatus("candidate").size());
  }

  @Test
  void human_promotion_publishes_an_upcoming_card() throws Exception {
    stubDataset(17, zipOf(EAU));
    service.syncAll(); // détecte EAU en candidat

    String lawId =
        service.promote(EAU, "eau", "2026-09-01", "Résumé éditorial vérifié.", "ressources naturelles");

    assertEquals(EAU, lawId);
    var law = laws.findById(EAU).orElseThrow();
    assertTrue(law.published());
    assertEquals("upcoming", law.status());
    // La carte apparaît maintenant côté public, en statut « à venir ».
    when()
        .get("/api/laws")
        .then()
        .statusCode(200)
        .body("findAll { it.status == 'upcoming' }.id", hasItem(EAU));
    assertEquals("promoted", candidates.findByUid(EAU).orElseThrow().status());
  }

  @Test
  void sync_endpoint_is_async_and_token_guarded() throws Exception {
    stubDataset(17, zipOf()); // zip vide : la passe de fond se termine vite
    // Sans token → refusé.
    when().post("/api/admin/dossiers/sync").then().statusCode(401);
    // Avec token → rend la main immédiatement (202 lancé, ou 200 si déjà en cours), jamais bloquant.
    given()
        .header("X-Admin-Token", "test-admin-token")
        .when()
        .post("/api/admin/dossiers/sync")
        .then()
        .statusCode(anyOf(is(202), is(200)));
  }

  @Test
  void promotion_refuses_a_terminated_dossier() {
    // Candidat présent mais dossier promulgué (terminated) : impossible à promouvoir en « à venir ».
    candidates.upsert(PROMULGUEE, 17, "Dossier clos", "https://x/dossiers/" + PROMULGUEE, "eau", true);
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () -> service.promote(PROMULGUEE, "agriculture", "2026-09-01", "x", "x"));
  }

  // --- helpers ---

  private static void stubDataset(int legislature, byte[] zip) {
    wireMock.stubFor(
        get(urlEqualTo("/opendata/" + legislature + "/Dossiers.json.zip"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/zip")
                    .withHeader("ETag", "\"dos-" + legislature + "\"")
                    .withBody(zip)));
  }

  private static byte[] zipOf(String... uids) throws Exception {
    var entries = new LinkedHashMap<String, byte[]>();
    for (String uid : uids) {
      entries.put(uid, Files.readAllBytes(FIXTURES.resolve(uid + ".json")));
    }
    var out = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      for (Map.Entry<String, byte[]> e : entries.entrySet()) {
        zos.putNextEntry(new ZipEntry("json/dossierParlementaire/" + e.getKey() + ".json"));
        zos.write(e.getValue());
        zos.closeEntry();
      }
    }
    return out.toByteArray();
  }

  private void insertUpcomingLaw(String id, String dossierUrl) throws Exception {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO laws (id, title, category, status, date, summary, source_url,"
                    + " source_expect, text_url, text_expect, published)"
                    + " VALUES (?, 'Carte à venir de test', 'agriculture', 'upcoming', '2026-01-01',"
                    + " 'résumé', ?, 'x', ?, 'x', 1)")) {
      ps.setString(1, id);
      ps.setString(2, dossierUrl);
      ps.setString(3, dossierUrl);
      ps.executeUpdate();
    }
  }

  private void insertPromotedCandidate(String uid, String lawId) throws Exception {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO dossier_candidates (uid, legislature, titre, dossier_url, theme,"
                    + " terminated, status, promoted_law_id) VALUES (?, 17, 'Dossier promu de test',"
                    + " 'https://www.assemblee-nationale.fr/dyn/17/dossiers/"
                    + uid
                    + "', 'agricole', 0, 'promoted', ?)")) {
      ps.setString(1, uid);
      ps.setString(2, lawId);
      ps.executeUpdate();
    }
  }
}

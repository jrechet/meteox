package fr.jrec.meteox.laws.opendata.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;

import fr.jrec.meteox.laws.opendata.CorpusSyncService;
import fr.jrec.meteox.laws.opendata.LawCandidateRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Couverture de TOUTES les actions admin du corpus des lois votées COMME un navigateur : chaque
 * requête porte l'en-tête {@code Origin} (la page admin est servie par le backend, un POST
 * navigateur envoie {@code Origin: https://jrec.fr}) — même DoD que {@link AdminDossierApiTest}.
 */
@QuarkusTest
class AdminCorpusApiTest {

  private static final String ADMIN = "test-admin-token"; // %test.meteox.admin.token
  private static final String ORIGIN = "https://jrec.fr"; // origine de la page admin
  private static final String CAND = "VTANR5L17V99999";
  private static final String VOTES =
      "{\"gauche\":{\"for\":1,\"against\":2,\"abstained\":3},"
          + "\"milieu\":{\"for\":4,\"against\":5,\"abstained\":6},"
          + "\"droite\":{\"for\":7,\"against\":8,\"abstained\":9},"
          + "\"extremeDroite\":{\"for\":10,\"against\":11,\"abstained\":12}}";

  @Inject LawCandidateRepository candidates;
  @Inject CorpusSyncService syncService;
  @Inject DataSource dataSource;

  @AfterEach
  void cleanup() throws Exception {
    // Le POST /sync est asynchrone (202) : on draine la passe de fond avant de rendre la main,
    // sinon un téléchargement encore en cours écrit dans le cache disque partagé alors qu'un
    // autre test l'efface (@BeforeEach) → course sur target/test-opendata. Voir DoD isolation.
    for (int i = 0; i < 200 && syncService.isRunning(); i++) {
      Thread.sleep(25);
    }
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM law_candidates WHERE uid LIKE 'VTANR5L17V9%'");
      st.executeUpdate("DELETE FROM scrutins WHERE law_id LIKE 'VTANR5L17V9%'");
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'VTANR5L17V9%'");
    }
  }

  /** Une requête telle que la page admin l'émet : origine jrec.fr + jeton. */
  private RequestSpecification browser() {
    return given().header("Origin", ORIGIN).header("X-Admin-Token", ADMIN);
  }

  private void seedCandidate() {
    candidates.upsert(
        CAND, 17, 99999, "l'ensemble du projet de loi de test sur l'eau.", "2025-06-01", "eau",
        "https://www.assemblee-nationale.fr/dyn/17/scrutins/99999", VOTES);
  }

  @Test
  void charger_candidates_depuis_origine_admin() {
    // Contrat CONSOMMÉ par la page admin : votes par bloc désérialisés, URL de scrutin officielle.
    seedCandidate();
    String cand = "find { it.uid == '" + CAND + "' }";
    browser()
        .when()
        .get("/api/admin/corpus/candidates")
        .then()
        .statusCode(200)
        .body(cand + ".dateScrutin", is("2025-06-01"))
        .body(cand + ".theme", is("eau"))
        .body(cand + ".scrutinUrl", is("https://www.assemblee-nationale.fr/dyn/17/scrutins/99999"))
        .body(cand + ".votes.gauche.for", is(1))
        .body(cand + ".votes.extremeDroite.abstained", is(12));
  }

  @Test
  void synchroniser_depuis_origine_admin_rend_la_main_immediatement() {
    browser().when().post("/api/admin/corpus/sync").then().statusCode(anyOf(is(202), is(200)));
  }

  @Test
  void promouvoir_depuis_origine_admin() {
    seedCandidate();
    browser()
        .contentType("application/json")
        .body(
            "{\"title\":\"Loi test eau\",\"category\":\"eau\",\"summary\":\"Résumé vérifié.\","
                + "\"sourceExpect\":\"projet de loi de test\","
                + "\"textUrl\":\"https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17N99999\","
                + "\"textExpect\":\"loi de test sur l'eau\"}")
        .when()
        .post("/api/admin/corpus/" + CAND + "/promote")
        .then()
        .statusCode(201);
  }

  @Test
  void promouvoir_sans_champs_requis_est_refuse() {
    // La publication exige titre, catégorie, résumé ET dossier vérifié (Golden Rule).
    seedCandidate();
    browser()
        .contentType("application/json")
        .body("{\"title\":\"Loi test\",\"category\":\"eau\"}")
        .when()
        .post("/api/admin/corpus/" + CAND + "/promote")
        .then()
        .statusCode(400);
  }

  @Test
  void promouvoir_un_candidat_inconnu_rend_404() {
    browser()
        .contentType("application/json")
        .body(
            "{\"title\":\"x\",\"category\":\"eau\",\"summary\":\"x\",\"textUrl\":\"https://x\","
                + "\"textExpect\":\"x\"}")
        .when()
        .post("/api/admin/corpus/VTANR5L17V90404/promote")
        .then()
        .statusCode(404);
  }

  @Test
  void rejeter_depuis_origine_admin() {
    seedCandidate();
    browser().when().post("/api/admin/corpus/" + CAND + "/reject").then().statusCode(204);
  }

  @Test
  void sans_jeton_non_autorise() {
    given()
        .header("Origin", ORIGIN)
        .when()
        .get("/api/admin/corpus/candidates")
        .then()
        .statusCode(401);
  }

  @Test
  void origine_inconnue_refusee_par_cors() {
    given()
        .header("Origin", "https://evil.example")
        .header("X-Admin-Token", ADMIN)
        .when()
        .post("/api/admin/corpus/sync")
        .then()
        .statusCode(403);
  }
}

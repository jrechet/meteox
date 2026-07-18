package fr.jrec.meteox.laws.opendata.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;

import fr.jrec.meteox.laws.opendata.DossierRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Couverture de TOUTES les actions admin dossiers COMME un navigateur : chaque requête porte
 * l'en-tête {@code Origin} (la page admin est servie par le backend, donc un POST navigateur
 * envoie {@code Origin: https://jrec.fr}). Ce test aurait attrapé le 403 CORS du bouton
 * « Synchroniser ». Couvre aussi l'authentification et le refus d'une origine inconnue.
 */
@QuarkusTest
class AdminDossierApiTest {

  private static final String ADMIN = "test-admin-token"; // %test.meteox.admin.token
  private static final String ORIGIN = "https://jrec.fr"; // origine de la page admin
  private static final String CAND = "DLR5L17N99999";

  @Inject DossierRepository candidates;
  @Inject DataSource dataSource;

  @AfterEach
  void cleanup() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM dossier_candidates WHERE uid LIKE 'DLR5L17N9%'");
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'DLR5L17N9%'");
    }
  }

  /** Une requête telle que la page admin l'émet : origine jrec.fr + jeton. */
  private RequestSpecification browser() {
    return given().header("Origin", ORIGIN).header("X-Admin-Token", ADMIN);
  }

  private void seedCandidate() {
    candidates.upsert(
        CAND, 17, "Dossier de test", "https://www.assemblee-nationale.fr/dyn/17/dossiers/" + CAND,
        "eau", false);
  }

  @Test
  void charger_candidates_depuis_origine_admin() {
    browser().when().get("/api/admin/dossiers/candidates").then().statusCode(200);
  }

  @Test
  void synchroniser_depuis_origine_admin_nest_pas_bloque_par_cors() {
    // LE bouton qui renvoyait 403 : un POST navigateur porte Origin: https://jrec.fr.
    browser().when().post("/api/admin/dossiers/sync").then().statusCode(anyOf(is(202), is(200)));
  }

  @Test
  void promouvoir_depuis_origine_admin() {
    seedCandidate();
    browser()
        .contentType("application/json")
        .body("{\"category\":\"eau\",\"date\":\"2026-09-01\",\"summary\":\"résumé\",\"sourceExpect\":\"test\"}")
        .when()
        .post("/api/admin/dossiers/" + CAND + "/promote")
        .then()
        .statusCode(201);
  }

  @Test
  void rejeter_depuis_origine_admin() {
    seedCandidate();
    browser().when().post("/api/admin/dossiers/" + CAND + "/reject").then().statusCode(204);
  }

  @Test
  void sans_jeton_non_autorise() {
    given()
        .header("Origin", ORIGIN)
        .when()
        .get("/api/admin/dossiers/candidates")
        .then()
        .statusCode(401);
  }

  @Test
  void origine_inconnue_refusee_par_cors() {
    given()
        .header("Origin", "https://evil.example")
        .header("X-Admin-Token", ADMIN)
        .when()
        .post("/api/admin/dossiers/sync")
        .then()
        .statusCode(403);
  }
}

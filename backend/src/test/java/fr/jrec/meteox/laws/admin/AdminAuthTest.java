package fr.jrec.meteox.laws.admin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

/**
 * Deux voies d'authentification admin (issue OAuth). La voie jeton est déjà couverte par les
 * {@code Admin*ApiTest} ; ici on couvre la voie <b>session GitHub</b> (identité simulée par
 * {@link TestSecurity}, telle que le navigateur la présente après login) et le refus sans identité
 * ni jeton. On tape un endpoint admin réel (dossiers) pour valider la garde partagée.
 */
@QuarkusTest
class AdminAuthTest {

  private static final String ENDPOINT = "/api/admin/dossiers/candidates";

  @Test
  @TestSecurity(
      user = "jrechet",
      roles = {AdminAuth.ADMIN_ROLE})
  void session_github_autorisee_accede_sans_jeton() {
    // Un admin connecté via GitHub (rôle admin) passe sans en-tête X-Admin-Token.
    given().when().get(ENDPOINT).then().statusCode(200);
  }

  @Test
  @TestSecurity(
      user = "intrus",
      roles = {"user"})
  void identite_github_non_admin_est_refusee() {
    // Une identité GitHub authentifiée mais hors allowlist (pas de rôle admin) et sans jeton → 401.
    given().when().get(ENDPOINT).then().statusCode(401);
  }

  @Test
  void sans_identite_ni_jeton_est_refuse() {
    given().when().get(ENDPOINT).then().statusCode(401);
  }

  @Test
  void jeton_de_secours_valide_accede() {
    // La voie de secours (automatisation) reste ouverte avec le jeton configuré en test.
    given()
        .header("X-Admin-Token", "test-admin-token")
        .when()
        .get(ENDPOINT)
        .then()
        .statusCode(200);
  }
}

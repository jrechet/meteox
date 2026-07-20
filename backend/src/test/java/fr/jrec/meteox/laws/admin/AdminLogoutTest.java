package fr.jrec.meteox.laws.admin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Déconnexion locale (issue OAuth) : {@code /admin/logout} efface la session puis redirige vers la
 * page admin. Hors OIDC (profil test), aucune session à effacer — on vérifie la redirection 303
 * relative, qui reste correcte derrière le préfixe Traefik.
 */
@QuarkusTest
class AdminLogoutTest {

  @Test
  void logout_redirige_vers_la_page_admin() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/admin/logout")
        .then()
        .statusCode(303)
        .header("Location", containsString("admin.html"));
  }
}

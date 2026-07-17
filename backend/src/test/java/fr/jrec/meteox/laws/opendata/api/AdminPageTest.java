package fr.jrec.meteox.laws.opendata.api;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** La page d'admin des dossiers est servie en statique par le backend (même origine que l'API). */
@QuarkusTest
class AdminPageTest {

  @Test
  void admin_page_is_served() {
    get("/admin.html")
        .then()
        .statusCode(200)
        .body(containsString("validation des dossiers"))
        .body(containsString("api/admin/dossiers"));
  }
}

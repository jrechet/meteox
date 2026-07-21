package fr.jrec.meteox.laws.opendata.senat.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;

import fr.jrec.meteox.laws.opendata.senat.SenatSyncService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Déclenchement admin de la sync Sénat COMME un navigateur : requête avec en-tête {@code Origin}
 * (même DoD que les autres admins — un POST navigateur porte {@code Origin: https://jrec.fr}).
 * Couvre auth (401) et refus d'origine inconnue (403 CORS).
 */
@QuarkusTest
class AdminSenatApiTest {

  private static final String ADMIN = "test-admin-token"; // %test.meteox.admin.token
  private static final String ORIGIN = "https://jrec.fr";

  @Inject SenatSyncService syncService;

  @AfterEach
  void drainAsyncSync() throws Exception {
    // Draine la passe async avant de rendre la main : un download de fond encore en cours
    // écrirait dans le cache disque partagé qu'un autre test efface → course.
    for (int i = 0; i < 200 && syncService.isRunning(); i++) {
      Thread.sleep(25);
    }
  }

  @Test
  void synchroniser_depuis_origine_admin_rend_la_main() {
    given()
        .header("Origin", ORIGIN)
        .header("X-Admin-Token", ADMIN)
        .when()
        .post("/api/admin/senat/sync")
        .then()
        .statusCode(anyOf(is(202), is(200)));
  }

  @Test
  void sans_jeton_non_autorise() {
    given()
        .header("Origin", ORIGIN)
        .when()
        .post("/api/admin/senat/sync")
        .then()
        .statusCode(401);
  }

  @Test
  void origine_inconnue_refusee_par_cors() {
    given()
        .header("Origin", "https://evil.example")
        .header("X-Admin-Token", ADMIN)
        .when()
        .post("/api/admin/senat/sync")
        .then()
        .statusCode(403);
  }
}

package fr.jrec.meteox.laws.indicators.api;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Acceptance issue #4 — workflow draft → published :
 * un score IA arrive en draft, n'apparaît JAMAIS dans GET /api/laws ni dans
 * GET /api/laws/{id}/indicators, et n'est publié qu'après validation humaine
 * (reviewed_by + horodatage), avec piste d'audit consultable. Le backend IA de test est le stub
 * claude-cli configuré dans application.properties (%test) — aucun appel réseau réel.
 */
@QuarkusTest
class IndicatorsWorkflowApiTest {

  private static final String ADMIN_TOKEN = "test-admin-token"; // %test.meteox.admin.token

  @Inject DataSource dataSource;

  @AfterEach
  void cleanupAiRows() throws Exception {
    // Les scores éditoriaux du seed ont model NULL ; tout score IA de test a un model.
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM indicator_scores WHERE model IS NOT NULL");
      st.executeUpdate("DELETE FROM indicator_audit");
    }
  }

  @Test
  void draft_scores_stay_invisible_until_human_review_publishes_them() {
    // 0. Endpoint public de transparence : scores publiés du seed, jamais de draft.
    when()
        .get("/api/laws/eco-agri-1/indicators")
        .then()
        .statusCode(200)
        .body("lawId", equalTo("eco-agri-1"))
        .body("methodology", equalTo(LawIndicatorsResource.METHODOLOGY_URL))
        .body("indicators.size()", equalTo(4))
        .body("indicators.status", everyItem(equalTo("published")));

    // 1. Extraction IA (admin) : crée 4 drafts.
    List<Integer> draftIds =
        given()
            .header("X-Admin-Token", ADMIN_TOKEN)
            .post("/api/admin/indicators/extract/eco-agri-1")
            .then()
            .statusCode(201)
            .extract()
            .path("draftIds");
    assertEquals(4, draftIds.size());
    long pesticidesDraftId = draftIds.get(0).longValue(); // ordre des axes du parser

    // 2. Un score draft n'apparaît JAMAIS côté public :
    //    GET /api/laws → indicateurs de la loi inchangés (valeurs éditoriales du seed) ;
    when()
        .get("/api/laws")
        .then()
        .statusCode(200)
        .body("find { it.id == 'eco-agri-1' }.indicators.pesticides", equalTo(1.0f))
        .body("find { it.id == 'eco-agri-1' }.indicators.pognonPuissants", equalTo(1.0f))
        .body("find { it.id == 'eco-agri-1' }.indicators.peupleSante", equalTo(1.5f))
        .body("find { it.id == 'eco-agri-1' }.indicators.partageEau", equalTo(1.5f));
    //    GET /api/laws/{id}/indicators → toujours 4 scores, tous published.
    when()
        .get("/api/laws/eco-agri-1/indicators")
        .then()
        .statusCode(200)
        .body("indicators.size()", equalTo(4))
        .body("indicators.status", everyItem(equalTo("published")));

    // 3. Les drafts sont visibles côté admin, avec justification citée et confiance.
    given()
        .header("X-Admin-Token", ADMIN_TOKEN)
        .get("/api/admin/indicators/drafts")
        .then()
        .statusCode(200)
        .body("id", hasItem(draftIds.get(0)))
        .body("find { it.id == %d }.citation".formatted(pesticidesDraftId), notNullValue())
        .body("find { it.id == %d }.confidence".formatted(pesticidesDraftId), equalTo("haute"))
        .body("find { it.id == %d }.reviewedBy".formatted(pesticidesDraftId), nullValue());

    // 4. Publication sans relecteur → refusée (validation humaine obligatoire).
    given()
        .header("X-Admin-Token", ADMIN_TOKEN)
        .contentType("application/json")
        .body("{}")
        .post("/api/admin/indicators/" + pesticidesDraftId + "/publish")
        .then()
        .statusCode(400);

    // 5. Publication avec relecteur humain → reviewed_by + horodatage.
    given()
        .header("X-Admin-Token", ADMIN_TOKEN)
        .contentType("application/json")
        .body("{\"reviewedBy\": \"jre\"}")
        .post("/api/admin/indicators/" + pesticidesDraftId + "/publish")
        .then()
        .statusCode(200)
        .body("status", equalTo("published"))
        .body("reviewedBy", equalTo("jre"))
        .body("reviewedAt", notNullValue());

    // 6. Le score validé apparaît maintenant côté public, avec justification et confiance.
    when()
        .get("/api/laws/eco-agri-1/indicators")
        .then()
        .statusCode(200)
        .body("indicators.size()", equalTo(5))
        .body("indicators.status", everyItem(equalTo("published")))
        .body(
            "indicators.find { it.id == %d }.citation".formatted(pesticidesDraftId),
            equalTo("restreindre la fabrication et la vente de produits contenant des PFAS"))
        .body("indicators.find { it.id == %d }.model".formatted(pesticidesDraftId),
            equalTo("claude-cli"));

    // 7. Double publication → conflit.
    given()
        .header("X-Admin-Token", ADMIN_TOKEN)
        .contentType("application/json")
        .body("{\"reviewedBy\": \"jre\"}")
        .post("/api/admin/indicators/" + pesticidesDraftId + "/publish")
        .then()
        .statusCode(409);

    // 8. Piste d'audit consultable : qui a créé/validé quoi, quand.
    given()
        .header("X-Admin-Token", ADMIN_TOKEN)
        .get("/api/admin/indicators/audit")
        .then()
        .statusCode(200)
        .body("action", hasItem("draft-created"))
        .body("action", hasItem("published"))
        .body("find { it.action == 'published' }.actor", equalTo("jre"))
        .body("find { it.action == 'published' }.scoreId", equalTo((int) pesticidesDraftId))
        .body("find { it.action == 'published' }.createdAt", notNullValue());
  }

  @Test
  void admin_endpoints_require_the_secret_token() {
    given().post("/api/admin/indicators/extract/eco-agri-1").then().statusCode(401);
    given()
        .header("X-Admin-Token", "mauvais-jeton")
        .get("/api/admin/indicators/drafts")
        .then()
        .statusCode(401);
    given().get("/api/admin/indicators/audit").then().statusCode(401);
  }

  @Test
  void indicators_endpoint_returns_404_for_unknown_law() {
    when().get("/api/laws/loi-inconnue/indicators").then().statusCode(404);
  }
}

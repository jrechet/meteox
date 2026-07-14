package fr.jrec.meteox.laws.sources;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.model.Law;
import fr.jrec.meteox.laws.repository.LawRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Acceptance issue #2 (job check-sources) : URL morte simulée → loi dépubliée (absente de
 * GET /api/laws), historique écrit dans source_checks, issue GitHub créée (API mockée WireMock).
 */
@QuarkusTest
class SourceCheckServiceTest {

  private static final int WIREMOCK_PORT = 18089; // aligné sur %test.meteox.github.api-base
  private static WireMockServer wireMock;

  @Inject SourceCheckService service;
  @Inject LawRepository repository;
  @Inject DataSource dataSource;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.options().port(WIREMOCK_PORT));
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @BeforeEach
  void resetStubs() {
    wireMock.resetAll();
    // API GitHub mockée : aucune issue ouverte, création acceptée.
    wireMock.stubFor(
        get(urlPathEqualTo("/repos/jrechet/meteox/issues"))
            .willReturn(okJson("[]")));
    wireMock.stubFor(
        post(urlEqualTo("/repos/jrechet/meteox/issues"))
            .willReturn(aResponse().withStatus(201).withBody("{\"number\": 99}")));
  }

  @AfterEach
  void cleanupTestLaws() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM source_checks WHERE law_id LIKE 'test-%'");
      st.executeUpdate("DELETE FROM scrutins WHERE law_id LIKE 'test-%'");
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'test-%'");
    }
  }

  @Test
  void dead_url_unpublishes_law_records_history_and_opens_github_issue() throws Exception {
    wireMock.stubFor(get(urlEqualTo("/source-dead")).willReturn(aResponse().withStatus(404)));
    wireMock.stubFor(
        get(urlEqualTo("/text-ok"))
            .willReturn(okJson("<html><title>Fragment officiel attendu</title></html>")));
    Law law = insertTestLaw("test-dead", base() + "/source-dead", base() + "/text-ok");

    service.checkLaw(law);

    // Loi dépubliée : absente de GET /api/laws, les 4 lois vérifiées restent seules exposées.
    when()
        .get("/api/laws")
        .then()
        .statusCode(200)
        .body("id", not(hasItem("test-dead")))
        .body("id", hasItems("eco-agri-1", "eco-eau-1", "eco-canicule-1", "eco-agri-loa"));
    assertFalse(repository.findById("test-dead").orElseThrow().published());

    // Historique dans source_checks : un échec HTTP 404 et un succès.
    Map<String, int[]> checks = loadChecks("test-dead");
    assertEquals(404, checks.get("sourceUrl")[0]);
    assertEquals(0, checks.get("sourceUrl")[1]);
    assertEquals(200, checks.get("textUrl")[0]);
    assertEquals(1, checks.get("textUrl")[1]);

    // Issue GitHub créée via l'API REST (mockée).
    wireMock.verify(
        1,
        postRequestedFor(urlEqualTo("/repos/jrechet/meteox/issues"))
            .withHeader("Authorization", containing("Bearer test-token"))
            .withRequestBody(containing("test-dead")));
  }

  @Test
  void wrong_content_with_http_200_also_unpublishes() throws Exception {
    // Golden Rule : un 200 qui ne contient pas le fragment officiel attendu est invalide.
    wireMock.stubFor(
        get(urlEqualTo("/source-wrong")).willReturn(okJson("<html>mauvais dossier</html>")));
    wireMock.stubFor(
        get(urlEqualTo("/text-ok"))
            .willReturn(okJson("<html>Fragment officiel attendu</html>")));
    Law law = insertTestLaw("test-wrong", base() + "/source-wrong", base() + "/text-ok");

    service.checkLaw(law);

    assertFalse(repository.findById("test-wrong").orElseThrow().published());
    Map<String, int[]> checks = loadChecks("test-wrong");
    assertEquals(200, checks.get("sourceUrl")[0]);
    assertEquals(0, checks.get("sourceUrl")[1]);
  }

  @Test
  void existing_open_issue_gets_a_comment_instead_of_duplicate() throws Exception {
    wireMock.stubFor(get(urlEqualTo("/source-dead")).willReturn(aResponse().withStatus(404)));
    wireMock.stubFor(
        get(urlEqualTo("/text-ok"))
            .willReturn(okJson("<html>Fragment officiel attendu</html>")));
    wireMock.stubFor(
        get(urlPathEqualTo("/repos/jrechet/meteox/issues"))
            .willReturn(
                okJson(
                    "[{\"number\": 42, \"title\": \"[check-sources] test-dup : source invalide\"}]")));
    wireMock.stubFor(
        post(urlEqualTo("/repos/jrechet/meteox/issues/42/comments"))
            .willReturn(aResponse().withStatus(201).withBody("{}")));
    Law law = insertTestLaw("test-dup", base() + "/source-dead", base() + "/text-ok");

    service.checkLaw(law);

    wireMock.verify(0, postRequestedFor(urlEqualTo("/repos/jrechet/meteox/issues")));
    wireMock.verify(
        1, postRequestedFor(urlEqualTo("/repos/jrechet/meteox/issues/42/comments")));
  }

  @Test
  void valid_sources_keep_the_law_published() throws Exception {
    wireMock.stubFor(
        get(urlEqualTo("/source-ok"))
            .willReturn(okJson("<html>Fragment officiel attendu</html>")));
    wireMock.stubFor(
        get(urlEqualTo("/text-ok"))
            .willReturn(okJson("<html>Fragment officiel attendu</html>")));
    Law law = insertTestLaw("test-ok", base() + "/source-ok", base() + "/text-ok");

    service.checkLaw(law);

    assertTrue(repository.findById("test-ok").orElseThrow().published());
    wireMock.verify(0, postRequestedFor(urlEqualTo("/repos/jrechet/meteox/issues")));
    Map<String, int[]> checks = loadChecks("test-ok");
    assertEquals(1, checks.get("sourceUrl")[1]);
    assertEquals(1, checks.get("textUrl")[1]);
  }

  private static String base() {
    return "http://localhost:" + WIREMOCK_PORT;
  }

  private Law insertTestLaw(String id, String sourceUrl, String textUrl) throws Exception {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO laws (id, title, category, status, date, summary, source_url,"
                    + " source_expect, text_url, text_expect, published)"
                    + " VALUES (?, ?, 'pesticides', 'passed', '2026-01-01', 'Loi de test', ?,"
                    + " 'Fragment officiel attendu', ?, 'Fragment officiel attendu', 1)")) {
      ps.setString(1, id);
      ps.setString(2, "Loi de test " + id);
      ps.setString(3, sourceUrl);
      ps.setString(4, textUrl);
      ps.executeUpdate();
    }
    return repository.findById(id).orElseThrow();
  }

  /** field → [http_status, ok] de la dernière vérification. */
  private Map<String, int[]> loadChecks(String lawId) throws Exception {
    var out = new java.util.HashMap<String, int[]>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT field, http_status, ok FROM source_checks WHERE law_id = ? ORDER BY id")) {
      ps.setString(1, lawId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.put(rs.getString("field"), new int[] {rs.getInt("http_status"), rs.getInt("ok")});
        }
      }
    }
    assertFalse(out.isEmpty(), "source_checks doit contenir l'historique pour " + lawId);
    return out;
  }
}

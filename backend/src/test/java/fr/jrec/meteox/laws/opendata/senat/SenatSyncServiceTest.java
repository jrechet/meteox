package fr.jrec.meteox.laws.opendata.senat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.model.SenatFacet;
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
 * Acceptance issue #3 (tâche 4, pipeline Sénat) : Dosleg + ODSEN_HISTOGROUPES + JSON de scrutin
 * servis par WireMock à partir des fixtures RÉELLES. Vérifie de bout en bout (a) la reproduction
 * chiffrée par bloc du scrutin 2022-125 pour la loi APER (règle CMP), (b) le cas « voté à main
 * levée » (PFAS, pas de scrutin public), et (c) la forme JSON de la facette {@code senat} exposée
 * par GET /api/laws.
 */
@QuarkusTest
class SenatSyncServiceTest {

  private static final int WIREMOCK_PORT = 18089;
  private static final Path FIX = Path.of("src", "test", "resources", "fixtures", "senat");
  private static WireMockServer wireMock;

  @Inject SenatSyncService service;
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
    stubOpenData();
    // Loi de test PFAS avec le lien AN sous forme SLUG (= forme du Dosleg) : appariement direct,
    // sans passer par le pont uid↔slug (couvert, lui, par SenatScrutinResolverTest).
    insertPassedLaw(
        "test-pfas",
        "https://www.assemblee-nationale.fr/dyn/16/dossiers/proteger_population_risques_pfas");
  }

  @AfterEach
  void cleanup() throws Exception {
    for (int i = 0; i < 200 && service.isRunning(); i++) {
      Thread.sleep(25);
    }
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      // Facette Sénat écrite sur les lois seedées ET de test : tout retirer (sinon LawsApiTest,
      // qui diffe GET /api/laws contre le snapshot committé, verrait un champ senat en trop).
      st.executeUpdate("DELETE FROM scrutins_senat");
      st.executeUpdate("DELETE FROM senat_lois");
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'test-%'");
    }
  }

  @Test
  void reproduces_aper_2022_125_by_bloc_and_publishes_the_senat_facet() {
    var report = service.syncAll();

    assertTrue(report.withScrutin() >= 1, "au moins APER a un scrutin public");
    assertTrue(report.noScrutin() >= 1, "au moins PFAS est à main levée");

    // eco-eau-1 = APER (seedée) : lien AN en uid, apparié directement au Dosleg → CMP 2022-125.
    SenatFacet senat = laws.findById("eco-eau-1").orElseThrow().senat();
    assertTrue(senat.hasPublicScrutin());
    assertEquals(2022, senat.session());
    assertEquals(125, senat.numero());
    assertEquals("2023-02-07", senat.scrutinDate());
    assertEquals(
        "https://www.senat.fr/scrutin-public/2022/scr2022-125.html", senat.scrutinUrl());

    Map<String, BlocVotes> v = senat.votes();
    assertEquals(new BlocVotes(64, 0, 27), v.get("gauche"));
    assertEquals(new BlocVotes(38, 0, 0), v.get("milieu"));
    assertEquals(new BlocVotes(184, 13, 3), v.get("droite")); // UC → droite
    assertEquals(new BlocVotes(0, 0, 0), v.get("extremeDroite")); // pas de groupe RN au Sénat
  }

  @Test
  void pfas_is_reported_as_no_public_scrutin_not_a_zero() {
    service.syncAll();

    SenatFacet senat = laws.findById("test-pfas").orElseThrow().senat();
    assertEquals(false, senat.hasPublicScrutin());
    assertEquals(SenatSyncService.NO_SCRUTIN_REASON, senat.reason());
    org.junit.jupiter.api.Assertions.assertNull(senat.votes(), "pas de votes : ce n'est pas un zéro");
  }

  @Test
  void api_exposes_the_senat_facet_json_shape() {
    service.syncAll();

    // Scrutin public : objet senat complet (booléen + session/numéro/url + votes par bloc).
    when()
        .get("/api/laws")
        .then()
        .statusCode(200)
        .body("find { it.id == 'eco-eau-1' }.senat.hasPublicScrutin", is(true))
        .body("find { it.id == 'eco-eau-1' }.senat.session", is(2022))
        .body("find { it.id == 'eco-eau-1' }.senat.numero", is(125))
        .body(
            "find { it.id == 'eco-eau-1' }.senat.scrutinUrl", containsString("scr2022-125.html"))
        .body("find { it.id == 'eco-eau-1' }.senat.votes.gauche", notNullValue())
        .body("find { it.id == 'eco-eau-1' }.senat.votes.droite.against", is(13))
        // Pas de scrutin public : booléen false + motif, aucun bloc de votes.
        .body("find { it.id == 'test-pfas' }.senat.hasPublicScrutin", is(false))
        .body("find { it.id == 'test-pfas' }.senat.reason", is(SenatSyncService.NO_SCRUTIN_REASON))
        .body("find { it.id == 'test-pfas' }.senat.votes", org.hamcrest.CoreMatchers.nullValue());
  }

  // --- helpers ---

  private static void stubOpenData() throws Exception {
    wireMock.stubFor(
        get(urlEqualTo("/opendata-senat/dosleg.zip"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/zip")
                    .withHeader("ETag", "\"dosleg-fix\"")
                    .withBody(zipOfSql(Files.readAllBytes(FIX.resolve("dosleg.sql"))))));
    wireMock.stubFor(
        get(urlEqualTo("/opendata-senat/ODSEN_HISTOGROUPES.json"))
            .willReturn(
                okJson(Files.readString(FIX.resolve("histogroupes.json")))
                    .withHeader("ETag", "\"histo-fix\"")));
    wireMock.stubFor(
        get(urlEqualTo("/scrutin-public/2022/scr2022-125.json"))
            .willReturn(
                okJson(Files.readString(FIX.resolve("scr2022-125.json")))
                    .withHeader("ETag", "\"scr-fix\"")));
  }

  /** Zippe le SQL Dosleg comme le fait le Sénat (une entrée .sql dans dosleg.zip). */
  private static byte[] zipOfSql(byte[] sql) throws Exception {
    var out = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      zos.putNextEntry(new ZipEntry("dosleg.sql"));
      zos.write(sql);
      zos.closeEntry();
    }
    return out.toByteArray();
  }

  private void insertPassedLaw(String id, String textUrl) throws Exception {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO laws (id, title, category, status, date, summary, source_url,"
                    + " source_expect, text_url, text_expect, published)"
                    + " VALUES (?, 'Loi de test', 'pesticides', 'passed', '2024-04-04', 'résumé',"
                    + " 'https://www.assemblee-nationale.fr/dyn/16/scrutins/3643', 'x', ?, 'x', 1)")) {
      ps.setString(1, id);
      ps.setString(2, textUrl);
      ps.executeUpdate();
    }
  }
}

package fr.jrec.meteox.laws.opendata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.repository.LawRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * Acceptance issue #3 (tâche 1, persistance + sync) : les zips open data sont servis par
 * WireMock à partir des fixtures réelles. Non-régression : la synchronisation ne modifie
 * PAS les votes vérifiés. Divergence : l'open data fait foi — votes écrasés, ancien
 * décompte archivé dans scrutin_syncs, issue GitHub ouverte (politique actée).
 */
@QuarkusTest
class ScrutinSyncServiceTest {

  private static final int WIREMOCK_PORT = 18089; // aligné sur %test.meteox.*
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/scrutins");
  private static WireMockServer wireMock;

  @Inject ScrutinSyncService service;
  @Inject LawRepository repository;
  @Inject DataSource dataSource;
  @Inject ObjectMapper mapper;

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
  void resetState() throws Exception {
    wireMock.resetAll();
    // Cache disque jetable : chaque test repart d'un état réseau connu.
    Path cache = Path.of("target/test-opendata");
    if (Files.exists(cache)) {
      try (var walk = Files.walk(cache)) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
      }
    }
    // API GitHub mockée : aucune issue ouverte, création acceptée.
    wireMock.stubFor(
        get(urlPathEqualTo("/repos/jrechet/meteox/issues")).willReturn(okJson("[]")));
    wireMock.stubFor(
        post(urlEqualTo("/repos/jrechet/meteox/issues"))
            .willReturn(aResponse().withStatus(201).withBody("{\"number\": 99}")));
  }

  @AfterEach
  void cleanup() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM scrutin_syncs");
      st.executeUpdate("DELETE FROM scrutins WHERE law_id LIKE 'test-%'");
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'test-%'");
    }
  }

  @Test
  void sync_from_official_open_data_keeps_verified_votes_intact() throws Exception {
    stubDataset(16, zipOf("VTANR5L16V3643.json", "VTANR5L16V823.json", "VTANR5L16V2721.json"));
    stubDataset(17, zipOf("VTANR5L17V844.json"));
    Map<String, Map<String, BlocVotes>> before = votesSnapshot();

    var report = service.syncAll();

    // Non-régression : l'agrégation open data reproduit EXACTEMENT les votes vérifiés.
    assertEquals(4, report.synced());
    assertEquals(0, report.changed());
    assertEquals(0, report.failed());
    assertEquals(before, votesSnapshot());

    // Chaque scrutin est journalisé avec son URL officielle /dyn/{lég}/scrutins/{n}.
    Map<String, String[]> syncs = loadSyncs();
    assertEquals(4, syncs.size());
    assertEquals(
        "https://www.assemblee-nationale.fr/dyn/17/scrutins/844",
        syncs.get("eco-agri-loa")[0]);
    for (String[] row : syncs.values()) {
      assertEquals("0", row[1], "aucune divergence attendue sur les fixtures officielles");
      assertNull(row[2], "old_votes doit rester NULL sans changement");
      assertNotNull(row[3]);
    }
    wireMock.verify(0, postRequestedFor(urlEqualTo("/repos/jrechet/meteox/issues")));
  }

  @Test
  void divergent_open_data_overwrites_votes_archives_old_and_opens_issue() throws Exception {
    // Scrutin fabriqué n°9999 (copie de la fixture 823) associé à une loi de test dont la
    // baseline en base diverge volontairement du décompte open data.
    byte[] tampered = withNumero("VTANR5L16V823.json", 9999);
    stubDataset(
        16,
        zipOf(
            Map.of(
                "VTANR5L16V3643.json", fixture("VTANR5L16V3643.json"),
                "VTANR5L16V823.json", fixture("VTANR5L16V823.json"),
                "VTANR5L16V2721.json", fixture("VTANR5L16V2721.json"),
                "VTANR5L16V9999.json", tampered)));
    stubDataset(17, zipOf("VTANR5L17V844.json"));
    insertTestLaw("test-diverge", "https://www.assemblee-nationale.fr/dyn/16/scrutins/9999");

    var report = service.syncAll();

    assertEquals(5, report.synced());
    assertEquals(1, report.changed());
    assertEquals(0, report.failed());

    // Les votes de la loi de test sont réalignés sur l'open data (plus les zéros baseline).
    Map<String, BlocVotes> updated = repository.votesFor("test-diverge");
    int total =
        updated.values().stream()
            .mapToInt(v -> v.votesFor() + v.votesAgainst() + v.votesAbstained())
            .sum();
    assertTrue(total > 0, "les votes doivent venir du décompte open data");

    // Historique : ancien décompte archivé, nouveau tracé.
    String[] row = loadSyncs().get("test-diverge");
    assertEquals("1", row[1]);
    assertTrue(row[2].contains("\"for\":0"), "old_votes doit archiver la baseline");
    assertTrue(row[3].contains("\"for\""));

    // Issue GitHub ouverte avec le label sync-scrutins (relecture humaine a posteriori).
    wireMock.verify(
        1,
        postRequestedFor(urlEqualTo("/repos/jrechet/meteox/issues"))
            .withRequestBody(WireMock.containing("sync-scrutins"))
            .withRequestBody(WireMock.containing("test-diverge")));
  }

  @Test
  void unchanged_dataset_is_revalidated_with_etag_not_redownloaded() throws Exception {
    stubDataset(16, zipOf("VTANR5L16V3643.json", "VTANR5L16V823.json", "VTANR5L16V2721.json"));
    stubDataset(17, zipOf("VTANR5L17V844.json"));
    service.syncAll();

    // Deuxième passe : le serveur répond 304 aux requêtes conditionnelles.
    wireMock.stubFor(
        get(urlEqualTo("/opendata/16/Scrutins.json.zip"))
            .withHeader("If-None-Match", equalTo("\"ds-16\""))
            .willReturn(aResponse().withStatus(304)));
    wireMock.stubFor(
        get(urlEqualTo("/opendata/17/Scrutins.json.zip"))
            .withHeader("If-None-Match", equalTo("\"ds-17\""))
            .willReturn(aResponse().withStatus(304)));

    var report = service.syncAll();

    assertEquals(4, report.synced());
    assertEquals(0, report.failed());
    wireMock.verify(
        getRequestedFor(urlEqualTo("/opendata/16/Scrutins.json.zip"))
            .withHeader("If-None-Match", equalTo("\"ds-16\"")));
  }

  // --- helpers ---

  private static void stubDataset(int legislature, byte[] zip) {
    wireMock.stubFor(
        get(urlEqualTo("/opendata/" + legislature + "/Scrutins.json.zip"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/zip")
                    .withHeader("ETag", "\"ds-" + legislature + "\"")
                    .withBody(zip)));
  }

  private static byte[] fixture(String name) throws Exception {
    return Files.readAllBytes(FIXTURES.resolve(name));
  }

  private static byte[] zipOf(String... fixtureNames) throws Exception {
    var entries = new LinkedHashMap<String, byte[]>();
    for (String name : fixtureNames) {
      entries.put(name, fixture(name));
    }
    return zipOf(entries);
  }

  private static byte[] zipOf(Map<String, byte[]> entries) throws Exception {
    var out = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      for (Map.Entry<String, byte[]> e : entries.entrySet()) {
        // Préfixe de dossier comme dans le zip AN réel (json/...).
        zos.putNextEntry(new ZipEntry("json/" + e.getKey()));
        zos.write(e.getValue());
        zos.closeEntry();
      }
    }
    return out.toByteArray();
  }

  /** Copie d'une fixture réelle avec un numéro de scrutin substitué (scrutin fabriqué). */
  private byte[] withNumero(String fixtureName, int numero) throws Exception {
    var root = mapper.readTree(fixture(fixtureName));
    ((ObjectNode) root.path("scrutin")).put("numero", String.valueOf(numero));
    return mapper.writeValueAsBytes(root);
  }

  private void insertTestLaw(String id, String scrutinUrl) throws Exception {
    try (Connection c = dataSource.getConnection()) {
      try (PreparedStatement ps =
          c.prepareStatement(
              "INSERT INTO laws (id, title, category, status, date, summary, source_url,"
                  + " source_expect, text_url, text_expect, published)"
                  + " VALUES (?, ?, 'pesticides', 'passed', '2026-01-01', 'Loi de test', ?,"
                  + " 'x', 'https://example.org/dossier', 'x', 1)")) {
        ps.setString(1, id);
        ps.setString(2, "Loi de test " + id);
        ps.setString(3, scrutinUrl);
        ps.executeUpdate();
      }
      // Baseline volontairement divergente : tous les blocs à zéro.
      try (PreparedStatement ps =
          c.prepareStatement(
              "INSERT INTO scrutins (law_id, bloc, votes_for, votes_against, votes_abstained)"
                  + " VALUES (?, ?, 0, 0, 0)")) {
        for (String bloc : new String[] {"gauche", "milieu", "droite", "extremeDroite"}) {
          ps.setString(1, id);
          ps.setString(2, bloc);
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }
  }

  /** law_id → votes par bloc, pour toutes les lois publiées. */
  private Map<String, Map<String, BlocVotes>> votesSnapshot() {
    var snapshot = new LinkedHashMap<String, Map<String, BlocVotes>>();
    for (var law : repository.findPublished()) {
      snapshot.put(law.id(), repository.votesFor(law.id()));
    }
    return snapshot;
  }

  /** law_id → [scrutin_url, changed, old_votes, new_votes] (dernière synchro). */
  private Map<String, String[]> loadSyncs() throws Exception {
    var out = new LinkedHashMap<String, String[]>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT law_id, scrutin_url, changed, old_votes, new_votes FROM scrutin_syncs"
                    + " ORDER BY id");
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.put(
            rs.getString("law_id"),
            new String[] {
              rs.getString("scrutin_url"),
              rs.getString("changed"),
              rs.getString("old_votes"),
              rs.getString("new_votes")
            });
      }
    }
    return out;
  }
}

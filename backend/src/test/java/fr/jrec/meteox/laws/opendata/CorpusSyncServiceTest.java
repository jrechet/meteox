package fr.jrec.meteox.laws.opendata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.repository.LawRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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
 * Acceptance issue #3 (tâche 3, sync-corpus) : les scrutins open data (fixtures réelles,
 * éventuellement altérées par Jackson pour fabriquer les cas limites) sont servis par WireMock.
 * On vérifie : détection des votes sur l'ensemble d'un texte de loi (solennels ET ordinaires —
 * la loi PFAS du corpus initial fut un scrutin ordinaire) à thème environnemental sur 2023+,
 * exclusions (hors thème, article isolé, rejeté, trop ancien, déjà couvert par une loi du
 * corpus), et la Golden Rule : RIEN n'est publié sans promotion humaine explicite.
 */
@QuarkusTest
class CorpusSyncServiceTest {

  private static final int WIREMOCK_PORT = 18089; // aligné sur %test.meteox.*
  private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures", "scrutins");
  /** Gabarit réel (16e, solennel, adopté) dont on dérive les scrutins de test. */
  private static final String TEMPLATE = "VTANR5L16V823.json";
  private static final String SOLENNEL = "scrutin public solennel";
  private static final String ORDINAIRE = "scrutin public ordinaire";
  private static WireMockServer wireMock;

  @Inject CorpusSyncService service;
  @Inject LawCandidateRepository candidates;
  @Inject LawRepository laws;
  @Inject DataSource dataSource;
  @Inject ObjectMapper mapper;

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
    stubDataset(17, zipOf()); // lég. 17 vide par défaut ; chaque test remplit la 16e
    Path cache = Path.of("target/test-opendata");
    if (Files.exists(cache)) {
      try (var walk = Files.walk(cache)) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
      }
    }
  }

  @AfterEach
  void cleanup() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM law_candidates");
      st.executeUpdate("DELETE FROM scrutins WHERE law_id LIKE 'VTANR%'");
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'VTANR%'");
    }
  }

  @Test
  void detects_solemn_themed_scrutins_and_excludes_off_theme_or_partial_votes() throws Exception {
    stubDataset(
        16,
        zipOf(
            // Solennel, thème énergie, 2023, adopté → candidat.
            scrutin("VTANR5L16V9001", 9001, "2023-06-01", SOLENNEL, "adopté",
                "l'ensemble du projet de loi relatif à la production d'énergies renouvelables."),
            // Solennel mais hors thème → exclu.
            scrutin("VTANR5L16V9002", 9002, "2023-06-02", SOLENNEL, "adopté",
                "l'ensemble du projet de loi portant réforme des retraites."),
            // Thématique mais vote sur un ARTICLE isolé (ni solennel ni « l'ensemble ») → exclu.
            scrutin("VTANR5L16V9003", 9003, "2023-06-03", ORDINAIRE, "adopté",
                "l'article 1er du projet de loi relatif à la gestion de l'eau.")));

    var report = service.syncAll();

    assertEquals(3, report.scanned());
    assertEquals(1, report.candidates());
    var staged = candidates.listByStatus("candidate");
    assertEquals(1, staged.size());
    var cand = staged.get(0);
    assertEquals("VTANR5L16V9001", cand.uid());
    assertEquals(16, cand.legislature());
    assertEquals("2023-06-01", cand.dateScrutin());
    assertTrue(cand.theme().startsWith("energ"), "thème détecté : " + cand.theme());
    assertTrue(
        cand.scrutinUrl().endsWith("/dyn/16/scrutins/9001"),
        "URL officielle du scrutin attendue : " + cand.scrutinUrl());
    assertTrue(cand.votesJson().contains("\"gauche\""), "votes par bloc capturés au scan");

    // Golden Rule : rien n'est publié — le staging n'expose AUCUNE loi côté public.
    when().get("/api/laws").then().statusCode(200).body("id", not(hasItem("VTANR5L16V9001")));
  }

  @Test
  void detects_ordinary_whole_text_votes_like_the_pfas_law() throws Exception {
    // La loi PFAS du corpus initial fut adoptée par scrutin public ORDINAIRE : le filtre couvre
    // aussi les votes sur « l'ensemble » hors procédure solennelle (apostrophe typographique
    // comprise, présente dans les données réelles de la 17e).
    stubDataset(
        16,
        zipOf(
            scrutin("VTANR5L16V9010", 9010, "2024-04-04", ORDINAIRE, "adopté",
                "l’ensemble de la proposition de loi visant à interdire les pesticides.")));

    var report = service.syncAll();

    assertEquals(1, report.candidates());
    assertEquals("VTANR5L16V9010", candidates.listByStatus("candidate").get(0).uid());
  }

  @Test
  void excludes_scrutins_before_2023_rejected_or_already_in_the_corpus() throws Exception {
    stubDataset(
        16,
        zipOf(
            // Avant la fenêtre 2023-2026 → exclu.
            scrutin("VTANR5L16V9020", 9020, "2022-05-12", SOLENNEL, "adopté",
                "l'ensemble du projet de loi relatif à l'agriculture durable."),
            // Rejeté : pas une loi VOTÉE (le statut public est 'passed') → exclu.
            scrutin("VTANR5L16V9021", 9021, "2023-06-24", SOLENNEL, "rejeté",
                "l'ensemble de la proposition de loi de programmation sur l'énergie."),
            // Scrutin 823 réel : déjà couvert par une loi du corpus (seed V2) → exclu.
            Map.entry(TEMPLATE, Files.readAllBytes(FIXTURES.resolve(TEMPLATE)))));

    var report = service.syncAll();

    assertEquals(3, report.scanned());
    assertEquals(0, report.candidates());
    assertTrue(candidates.listByStatus("candidate").isEmpty());
  }

  @Test
  void unknown_organe_fails_loudly_for_that_scrutin_without_aborting_the_scan() throws Exception {
    // Golden Rule : un organeRef inconnu ne fausse jamais un total — le scrutin est écarté
    // (compté en échec, loggé), les autres continuent.
    byte[] tampered =
        withOrganeRef(
            scrutin("VTANR5L16V9030", 9030, "2023-09-01", SOLENNEL, "adopté",
                "l'ensemble du projet de loi sur la biodiversité."),
            "PO000000");
    stubDataset(
        16,
        zipOf(
            Map.entry("VTANR5L16V9030.json", tampered),
            scrutin("VTANR5L16V9031", 9031, "2023-09-02", SOLENNEL, "adopté",
                "l'ensemble du projet de loi sur les forêts.")));

    var report = service.syncAll();

    assertEquals(1, report.failed());
    assertEquals(1, report.candidates());
    assertEquals("VTANR5L16V9031", candidates.listByStatus("candidate").get(0).uid());
  }

  @Test
  void rescanning_is_idempotent_and_reconciliation_removes_stale_candidates() throws Exception {
    stubDataset(
        16,
        zipOf(
            scrutin("VTANR5L16V9040", 9040, "2023-06-01", SOLENNEL, "adopté",
                "l'ensemble du projet de loi relatif au climat.")));
    service.syncAll();
    service.syncAll();
    assertEquals(1, candidates.listByStatus("candidate").size());

    // Nouveau scan avec un AUTRE scrutin : le candidat non actionné disparaît (réconciliation).
    stubDataset(
        16,
        zipOf(
            scrutin("VTANR5L16V9041", 9041, "2023-06-02", SOLENNEL, "adopté",
                "l'ensemble du projet de loi relatif à la pollution plastique.")));
    service.syncAll();

    var uids =
        candidates.listByStatus("candidate").stream()
            .map(LawCandidateRepository.Candidate::uid)
            .toList();
    assertEquals(java.util.List.of("VTANR5L16V9041"), uids);
  }

  @Test
  void human_promotion_publishes_a_passed_law_with_scrutin_votes_and_official_urls()
      throws Exception {
    // Gabarit 823 renuméroté : les votes agrégés attendus sont ceux (vérifiés) de la fixture.
    stubDataset(
        16,
        zipOf(
            scrutin("VTANR5L16V9050", 9050, "2023-06-01", SOLENNEL, "adopté",
                "l'ensemble du projet de loi relatif aux énergies renouvelables (test).")));
    service.syncAll();

    String lawId =
        service.promote(
            "VTANR5L16V9050",
            new CorpusSyncService.Promotion(
                "Loi énergies renouvelables (test)",
                "agriculture",
                "Résumé éditorial vérifié.",
                "énergies renouvelables",
                "https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N44343",
                "accélération de la production d'énergies renouvelables"));

    assertEquals("VTANR5L16V9050", lawId);
    var law = laws.findById(lawId).orElseThrow();
    assertTrue(law.published());
    assertEquals("passed", law.status());
    assertEquals("2023-06-01", law.date());
    assertEquals("https://www.assemblee-nationale.fr/dyn/16/scrutins/9050", law.sourceUrl());
    assertEquals(
        "https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N44343", law.textUrl());
    // Votes par bloc : décompte officiel de la fixture 823 (vérifié dans ScrutinExtractionTest).
    Map<String, BlocVotes> votes = laws.votesFor(lawId);
    assertEquals(new BlocVotes(28, 91, 24), votes.get("gauche"));
    assertEquals(new BlocVotes(257, 2, 6), votes.get("milieu"));
    assertEquals(new BlocVotes(1, 56, 4), votes.get("droite"));
    assertEquals(new BlocVotes(0, 87, 0), votes.get("extremeDroite"));
    // La loi apparaît côté public, et le candidat est marqué promu.
    when().get("/api/laws").then().statusCode(200).body("id", hasItem(lawId));
    assertEquals("promoted", candidates.findByUid(lawId).orElseThrow().status());
  }

  @Test
  void promotion_refuses_unknown_or_already_promoted_candidates() throws Exception {
    stubDataset(
        16,
        zipOf(
            scrutin("VTANR5L16V9060", 9060, "2023-06-01", SOLENNEL, "adopté",
                "l'ensemble du projet de loi relatif aux zones humides.")));
    service.syncAll();
    var promotion =
        new CorpusSyncService.Promotion(
            "Titre", "eau", "Résumé.", "zones humides", "https://x/dossiers/D1", "fragment");

    assertThrows(IllegalArgumentException.class, () -> service.promote("VTANR5L16V0000", promotion));
    service.promote("VTANR5L16V9060", promotion);
    assertThrows(IllegalStateException.class, () -> service.promote("VTANR5L16V9060", promotion));
  }

  @Test
  void rejected_candidate_is_no_longer_proposed() throws Exception {
    stubDataset(
        16,
        zipOf(
            scrutin("VTANR5L16V9070", 9070, "2023-06-01", SOLENNEL, "adopté",
                "l'ensemble du projet de loi relatif aux nappes phréatiques.")));
    service.syncAll();

    service.reject("VTANR5L16V9070");

    assertTrue(candidates.listByStatus("candidate").isEmpty());
    assertEquals("rejected", candidates.findByUid("VTANR5L16V9070").orElseThrow().status());
    // Un candidat rejeté survit à la réconciliation du scan suivant (décision humaine conservée).
    service.syncAll();
    assertFalse(candidates.findByUid("VTANR5L16V9070").isEmpty());
  }

  // --- helpers ---

  private static void stubDataset(int legislature, byte[] zip) {
    wireMock.stubFor(
        get(urlEqualTo("/opendata/" + legislature + "/Scrutins.json.zip"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/zip")
                    .withHeader("ETag", "\"corpus-" + legislature + "\"")
                    .withBody(zip)));
  }

  /**
   * Scrutin de test dérivé du gabarit réel 823 (ventilation par groupe conservée), avec uid,
   * numéro, date, type de vote, sort et titre substitués — pour fabriquer chaque cas limite
   * sans committer de nouvelles fixtures.
   */
  private Map.Entry<String, byte[]> scrutin(
      String uid, int numero, String date, String typeVote, String sort, String titre)
      throws Exception {
    var root = mapper.readTree(Files.readAllBytes(FIXTURES.resolve(TEMPLATE)));
    ObjectNode s = (ObjectNode) root.path("scrutin");
    s.put("uid", uid);
    s.put("numero", String.valueOf(numero));
    s.put("dateScrutin", date);
    ((ObjectNode) s.path("typeVote")).put("libelleTypeVote", typeVote);
    ((ObjectNode) s.path("sort")).put("code", sort);
    s.put("titre", titre);
    return Map.entry(uid + ".json", mapper.writeValueAsBytes(root));
  }

  /** Remplace l'organeRef du premier groupe de la ventilation (fabrique un organe inconnu). */
  private byte[] withOrganeRef(Map.Entry<String, byte[]> entry, String organeRef) throws Exception {
    var root = mapper.readTree(entry.getValue());
    ObjectNode premier =
        (ObjectNode)
            root.path("scrutin")
                .path("ventilationVotes")
                .path("organe")
                .path("groupes")
                .path("groupe")
                .get(0);
    premier.put("organeRef", organeRef);
    return mapper.writeValueAsBytes(root);
  }

  @SafeVarargs
  private static byte[] zipOf(Map.Entry<String, byte[]>... entries) throws Exception {
    var ordered = new LinkedHashMap<String, byte[]>();
    for (var e : entries) {
      ordered.put(e.getKey(), e.getValue());
    }
    var out = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      for (var e : ordered.entrySet()) {
        // Préfixe de dossier comme dans le zip AN réel (json/...).
        zos.putNextEntry(new ZipEntry("json/" + e.getKey()));
        zos.write(e.getValue());
        zos.closeEntry();
      }
    }
    return out.toByteArray();
  }
}

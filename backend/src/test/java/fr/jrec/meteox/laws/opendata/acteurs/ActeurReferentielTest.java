package fr.jrec.meteox.laws.opendata.acteurs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.jrec.meteox.laws.opendata.acteurs.ActeurReferentiel.GroupeAffiliation;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Acceptance sous-issue #34 (module A) : le référentiel résout {@code acteurRef} → nom + groupe
 * politique (sigle, organeRef, bloc). Le jeu AMO30 est servi par WireMock à partir des fixtures
 * réelles (acteurs + organes), le bloc étant déduit du mapping committé {@code organe-blocs.json}.
 * Cas couverts : résolution complète (nom+groupe+bloc), groupe absent (pas de mandat GP actif),
 * organeRef hors mapping (bloc nul), acteur inconnu.
 */
@QuarkusTest
@TestProfile(ActeurReferentielTest.AmoProfile.class)
class ActeurReferentielTest {

  private static final int WIREMOCK_PORT = 18089; // aligné sur %test.meteox.*
  private static final String ZIP_URL = "/opendata/17/Acteurs.json.zip";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static WireMockServer wireMock;

  @Inject ActeurReferentiel referentiel;

  /** Pointe le jeu AMO30 vers WireMock (application.properties reste réservé à l'intégration C). */
  public static class AmoProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "meteox.opendata.acteurs.url-template", "http://localhost:18089/opendata/%d/Acteurs.json.zip");
    }
  }

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
    wireMock.stubFor(
        get(urlEqualTo(ZIP_URL))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/zip")
                    .withHeader("ETag", "\"amo-17\"")
                    .withBody(amoZip())));
  }

  @Test
  void resolves_name_group_and_bloc_for_actor_with_active_mandate() {
    // Stéphane Viry → LIOT (PO845485) → bloc milieu (présent dans organe-blocs.json).
    GroupeAffiliation g = referentiel.groupeDe("PA721474").orElseThrow();

    assertEquals("LIOT", g.sigle());
    assertEquals("PO845485", g.organeRef());
    assertEquals("milieu", g.bloc());
    assertEquals("Stéphane Viry", referentiel.nomDe("PA721474").orElseThrow());
  }

  @Test
  void resolves_a_different_group_to_its_own_bloc() {
    // Alain David → SOC (PO845419) → bloc gauche.
    GroupeAffiliation g = referentiel.groupeDe("PA1008").orElseThrow();

    assertEquals("SOC", g.sigle());
    assertEquals("PO845419", g.organeRef());
    assertEquals("gauche", g.bloc());
  }

  @Test
  void empty_group_for_actor_without_active_group_mandate() {
    // Marc-Philippe Daubresse : aucun mandat GP actif → pas de groupe, mais le nom reste connu.
    assertTrue(referentiel.groupeDe("PA1001").isEmpty(), "aucun groupe actif attendu");
    assertEquals("Marc-Philippe Daubresse", referentiel.nomDe("PA1001").orElseThrow());
  }

  @Test
  void null_bloc_when_organe_ref_absent_from_bloc_mapping() {
    // Éric Ciotti → UDDPLR (PO872880), organeRef absent d'organe-blocs.json → groupe résolu, bloc nul.
    GroupeAffiliation g = referentiel.groupeDe("PA330240").orElseThrow();

    assertEquals("UDDPLR", g.sigle());
    assertEquals("PO872880", g.organeRef());
    assertNull(g.bloc(), "bloc doit être nul quand l'organeRef n'est pas mappé");
  }

  @Test
  void unknown_actor_ref_resolves_to_empty() {
    Optional<GroupeAffiliation> g = referentiel.groupeDe("PA000000");
    assertTrue(g.isEmpty());
    assertTrue(referentiel.nomDe("PA000000").isEmpty());
  }

  // --- helpers ---

  /** Zip AMO30 en mémoire : entrées json/acteur/PA*.json et json/organe/PO*.json (comme le zip AN). */
  private static byte[] amoZip() throws Exception {
    var entries = new LinkedHashMap<String, byte[]>();
    for (String uid : new String[] {"PA721474", "PA1008", "PA1001", "PA330240"}) {
      entries.put("json/acteur/" + uid + ".json", fixture("acteurs/" + uid + ".json"));
    }
    for (String uid : new String[] {"PO845485", "PO845419", "PO872880"}) {
      entries.put("json/organe/" + uid + ".json", fixture("organes/" + uid + ".json"));
    }
    var out = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      for (Map.Entry<String, byte[]> e : entries.entrySet()) {
        zos.putNextEntry(new ZipEntry(e.getKey()));
        zos.write(e.getValue());
        zos.closeEntry();
      }
    }
    return out.toByteArray();
  }

  private static byte[] fixture(String relative) throws Exception {
    return Files.readAllBytes(FIXTURES.resolve(relative));
  }
}

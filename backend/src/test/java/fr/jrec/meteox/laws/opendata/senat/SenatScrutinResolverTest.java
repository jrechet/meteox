package fr.jrec.meteox.laws.opendata.senat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import fr.jrec.meteox.laws.opendata.senat.SenatResolution.Kind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Résolution loi meteox → scrutin public au Sénat sur la fixture Dosleg réelle. Couvre : la règle
 * « dernière lecture / CMP », le rattrapage uid↔slug (cas réel PFAS), et l'absence de scrutin
 * public. Pas de réseau : le pont uid↔slug est injecté à la main (comme le ferait l'open data AN).
 */
class SenatScrutinResolverTest {

  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/senat/dosleg.sql");
  private DoslegDataset dataset;

  @BeforeEach
  void loadFixture() throws IOException {
    try (InputStream in = Files.newInputStream(FIXTURE)) {
      dataset = new DoslegParser().parse(in);
    }
  }

  private SenatScrutinResolver resolver(AnDossierAliases aliases) {
    SenatScrutinResolver r = new SenatScrutinResolver();
    r.aliases = aliases;
    return r;
  }

  @Test
  void resolves_aper_to_the_cmp_scrutin_last_reading() {
    // APER : lien AN en uid (match direct, sans pont). Deux scrutins sur l'ensemble → règle CMP :
    // on publie la CMP (2022-125), pas la 1ʳᵉ lecture (2022-29).
    SenatResolution r =
        resolver((leg, ref) -> Set.of())
            .resolve(
                "https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N46539", dataset);

    assertEquals(Kind.RESOLVED, r.kind());
    assertNotNull(r.scrutin());
    assertEquals(2022, r.scrutin().session());
    assertEquals(125, r.scrutin().numero());
    assertEquals("2023-02-07", r.scrutin().scrutinDate());
    assertEquals(
        "https://www.senat.fr/scrutin-public/2022/scr2022-125.html", r.scrutin().scrutinUrl());
  }

  @Test
  void bridges_uid_to_slug_and_reports_no_public_scrutin_for_pfas() {
    // PFAS réel : meteox stocke l'uid (DLR5L16N49455), le Dosleg porte le slug
    // (proteger_population_risques_pfas). Le pont uid↔slug rattrape la correspondance ; la loi
    // existe côté Sénat mais n'a AUCUN scrutin sur l'ensemble (voté à main levée).
    AnDossierAliases bridge =
        (leg, ref) ->
            leg == 16 && ref.equals("dlr5l16n49455")
                ? Set.of("proteger_population_risques_pfas")
                : Set.of();
    SenatResolution r =
        resolver(bridge)
            .resolve(
                "https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N49455", dataset);

    assertEquals(Kind.NO_PUBLIC_SCRUTIN, r.kind());
    assertNull(r.scrutin());
  }

  @Test
  void unresolved_when_the_uid_slug_bridge_is_unavailable() {
    // Sans pont, l'uid PFAS ne matche pas le slug Dosleg : la loi reste non résolue (jamais un
    // faux « pas de scrutin »).
    SenatResolution r =
        resolver((leg, ref) -> Set.of())
            .resolve(
                "https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N49455", dataset);
    assertEquals(Kind.UNRESOLVED, r.kind());
  }

  @Test
  void unresolved_when_no_dosleg_loi_matches() {
    SenatResolution r =
        resolver((leg, ref) -> Set.of())
            .resolve(
                "https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17N99999", dataset);
    assertEquals(Kind.UNRESOLVED, r.kind());
  }
}

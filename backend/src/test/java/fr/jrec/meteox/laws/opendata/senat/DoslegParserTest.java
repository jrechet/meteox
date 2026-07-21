package fr.jrec.meteox.laws.opendata.senat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jrec.meteox.laws.opendata.senat.DoslegDataset.EnsembleScrutin;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parsing du dump Dosleg (format COPY PostgreSQL). Fixture réelle minimale (extraite du dump du
 * 2026-07-21) : chaînes APER (2 scrutins « sur l'ensemble ») et PFAS (amendements seuls). Vérifie
 * aussi l'indexation par NOM de colonne sur l'en-tête réel de {@code loi} (44 colonnes) et l'échec
 * bruyant si une colonne attendue disparaît.
 */
class DoslegParserTest {

  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/senat/dosleg.sql");

  private DoslegDataset parseFixture() throws IOException {
    try (InputStream in = Files.newInputStream(FIXTURE)) {
      return new DoslegParser().parse(in);
    }
  }

  @Test
  void indexes_loi_by_an_ref_in_both_uid_and_slug_forms() throws IOException {
    DoslegDataset d = parseFixture();

    // APER : lien AN sous forme uid (comme le text_url meteox correspondant).
    var aper =
        d.loiByAnRef(
            DoslegDataset.normalizeAnRef(
                "https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N46539"));
    assertTrue(aper.isPresent(), "APER doit être indexée par son uid AN");
    assertEquals("70063", aper.get().loicod());
    assertEquals("pjl21-889", aper.get().signet());

    // PFAS : lien AN sous forme slug (titreChemin).
    var pfas = d.loiByAnRef(DoslegDataset.normalizeAnRef("proteger_population_risques_pfas"));
    assertTrue(pfas.isPresent(), "PFAS doit être indexée par son slug AN");
    assertEquals("73906", pfas.get().loicod());
  }

  @Test
  void resolves_only_sur_l_ensemble_scrutins_along_the_chain() throws IOException {
    DoslegDataset d = parseFixture();

    // APER : la chaîne loi→lecture→lecass→date_seance→scr contient 2 scrutins « sur l'ensemble »
    // (1ʳᵉ lecture 2022-29 et CMP 2022-125) parmi les scrutins d'amendements — seuls les deux
    // « ensemble » sont retenus.
    List<EnsembleScrutin> aper = d.ensembleScrutins("70063");
    assertEquals(2, aper.size(), "APER : 2 scrutins sur l'ensemble");
    assertTrue(aper.stream().anyMatch(s -> s.session() == 2022 && s.numero() == 125));
    assertTrue(aper.stream().anyMatch(s -> s.session() == 2022 && s.numero() == 29));
    EnsembleScrutin cmp =
        aper.stream().filter(s -> s.numero() == 125).findFirst().orElseThrow();
    assertEquals("2023-02-07", cmp.scrutinDate());
    assertTrue(cmp.leccom().toLowerCase().startsWith("commission mixte paritaire"));

    // PFAS : uniquement des scrutins d'amendements → AUCUN « sur l'ensemble ».
    assertEquals(0, d.ensembleScrutins("73906").size(), "PFAS : aucun scrutin sur l'ensemble");
  }

  @Test
  void extracts_columns_by_name_from_the_real_loi_header_and_trims_padding() throws IOException {
    // En-tête réel de la table loi (44 colonnes) : url_an est en position 33 (index 32).
    String header =
        "loicod, typloicod, etaloicod, deccoccod, numero, signet, loient, motclef, loitit, loiint,"
            + " urgence, url_jo, loinumjo, loidatjo, date_loi, loititjo, url_jo2, loinumjo2,"
            + " loidatjo2, deccocurl, num_decision, date_decision, loicodmai, loinoudelibcod,"
            + " motionloiorigcod, objet, url_ordonnance, saisine_date, saisine_par, loidatjo3,"
            + " loinumjo3, url_jo3, url_an, url_presart, signetalt, orgcod, doscocurl, loiintori,"
            + " proaccdat, proaccoppdat, retproaccdat, en_clair_url, en_clair_image, en_clair_chapo";
    String[] fields = new String[44];
    java.util.Arrays.fill(fields, "\\N");
    fields[0] = "70063   "; // colonne CHAR complétée par des espaces → doit être trimée
    fields[5] = "pjl21-889";
    fields[32] = "http://www.assemblee-nationale.fr/16/dossiers/DLR5L16N46539.asp";
    String sql =
        "COPY loi (" + header + ") FROM stdin;\n" + String.join("\t", fields) + "\n\\.\n";

    DoslegDataset d =
        new DoslegParser().parse(new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)));

    var loi = d.loiByAnRef("dlr5l16n46539");
    assertTrue(loi.isPresent());
    assertEquals("70063", loi.get().loicod(), "la valeur CHAR paddée doit être trimée");
  }

  @Test
  void fails_loudly_when_an_expected_column_disappears() {
    // En-tête loi SANS url_an : le parser doit échouer (format non contractuel, note § 6).
    String sql = "COPY loi (loicod, signet) FROM stdin;\n70063\tpjl21-889\n\\.\n";
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                new DoslegParser()
                    .parse(new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8))));
    assertTrue(ex.getMessage().contains("url_an"));
  }

  @Test
  void skips_unwanted_copy_blocks_including_votsen() throws IOException {
    // Un bloc volumineux non voulu (votsen) est sauté sans interférer avec les blocs utiles.
    String sql =
        "COPY votsen (a, b, c) FROM stdin;\n1\t2\t3\n4\t5\t6\n\\.\n"
            + "COPY loi (loicod, signet, url_an) FROM stdin;\n"
            + "70063\tpjl21-889\thttp://www.assemblee-nationale.fr/16/dossiers/DLR5L16N46539.asp\n"
            + "\\.\n";
    DoslegDataset d =
        new DoslegParser().parse(new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, d.loiCount());
    assertTrue(d.loiByAnRef("dlr5l16n46539").isPresent());
  }
}

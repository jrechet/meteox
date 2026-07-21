package fr.jrec.meteox.laws.opendata.senat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.opendata.senat.SenatScrutinParser.SenatVote;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reproduction chiffrée du scrutin Sénat 2022-125 (CMP APER) — l'équivalent Sénat de la
 * vérification AN sur le scrutin 844. À partir des fixtures RÉELLES (votes nominatifs + historique
 * des groupes), l'agrégat PAR BLOC (mapping arrêté : UC→droite, NI→extremeDroite, RDSE EXCLU)
 * reproduit l'analyse officielle par groupe, 0 matricule inconnu. Couvre aussi le mapping et
 * l'échec bruyant sur groupe inconnu (Golden Rule).
 */
@QuarkusTest
class SenatVoteAggregationTest {

  private static final Path FIX = Path.of("src/test/resources/fixtures/senat");
  private static final LocalDate SCRUTIN_2022_125 = LocalDate.of(2023, 2, 7);

  @Inject SenatScrutinVotes votes;
  @Inject SenatBlocMapping mapping;
  @Inject ObjectMapper mapper;

  private HistoGroupes histoFixture() throws Exception {
    return HistoGroupes.parse(mapper.readTree(FIX.resolve("histogroupes.json").toFile()));
  }

  @Test
  void reproduces_scrutin_2022_125_by_bloc_zero_unknown() throws Exception {
    byte[] json = Files.readAllBytes(FIX.resolve("scr2022-125.json"));

    Map<String, BlocVotes> byBloc =
        votes.aggregate(json, SCRUTIN_2022_125, histoFixture(), 2022, 125);

    // gauche = SOC(64/0/0) + CRC(0/0/15) + GEST(0/0/12)
    assertEquals(new BlocVotes(64, 0, 27), byBloc.get("gauche"));
    // milieu = LREM/RDPI(24/0/0) + RTLI/Indépendants(14/0/0)
    assertEquals(new BlocVotes(38, 0, 0), byBloc.get("milieu"));
    // droite = UMP/LR(133/10/0) + UC(51/3/3)  — décision : UC → droite
    assertEquals(new BlocVotes(184, 13, 3), byBloc.get("droite"));
    // extremeDroite = NI/RN : aucun votant NI sur ce scrutin → bloc structurellement vide
    assertEquals(new BlocVotes(0, 0, 0), byBloc.get("extremeDroite"));

    // RDSE (14 pour) est EXCLU des agrégats (horsBlocs) : total pour des blocs = 286, pas 300.
    int totalPour = byBloc.values().stream().mapToInt(BlocVotes::votesFor).sum();
    assertEquals(286, totalPour, "les 14 « pour » du RDSE ne sont pas comptés dans les blocs");

    assertEquals(
        List.of("gauche", "milieu", "droite", "extremeDroite"), List.copyOf(byBloc.keySet()));
  }

  @Test
  void mapping_follows_the_agreed_decisions() {
    assertEquals("gauche", mapping.blocFor("SOC").orElseThrow());
    assertEquals("gauche", mapping.blocFor("CRC").orElseThrow());
    assertEquals("gauche", mapping.blocFor("GEST").orElseThrow());
    assertEquals("milieu", mapping.blocFor("LREM").orElseThrow());
    assertEquals("milieu", mapping.blocFor("RTLI").orElseThrow());
    assertEquals("droite", mapping.blocFor("UMP").orElseThrow());
    assertEquals("droite", mapping.blocFor("UC").orElseThrow()); // décision utilisateur
    assertEquals("extremeDroite", mapping.blocFor("NI").orElseThrow()); // décision utilisateur
    assertTrue(mapping.isHorsBlocs("RDSE"), "RDSE exclu des agrégats (décision utilisateur)");
    assertTrue(mapping.isKnown("RDSE"));
    assertTrue(mapping.blocFor("INCONNU").isEmpty());
    assertTrue(!mapping.isKnown("INCONNU"));
  }

  @Test
  void unknown_groupe_fails_loudly_instead_of_skewing_totals() throws Exception {
    // Un matricule dont le groupe (à la date) n'est pas référencé → échec explicite.
    HistoGroupes histo =
        HistoGroupes.parse(
            mapper.readTree(
                "{\"results\":[{\"Matricule\":\"X1\",\"Code_du_groupe_politique\":\"ZZZ\","
                    + "\"Date_de_debut_d_appartenance\":null,\"Date_de_fin_d_appartenance\":null}]}"));
    UnknownGroupeSenatException ex =
        assertThrows(
            UnknownGroupeSenatException.class,
            () ->
                votes.aggregate(
                    List.of(new SenatVote("X1", "p")), SCRUTIN_2022_125, histo, 2022, 999));
    assertTrue(ex.getMessage().contains("ZZZ"));
  }

  @Test
  void voter_without_group_at_date_fails_loudly() throws Exception {
    HistoGroupes empty = HistoGroupes.parse(mapper.readTree("{\"results\":[]}"));
    UnknownGroupeSenatException ex =
        assertThrows(
            UnknownGroupeSenatException.class,
            () ->
                votes.aggregate(
                    List.of(new SenatVote("GHOST", "p")), SCRUTIN_2022_125, empty, 2022, 999));
    assertTrue(ex.getMessage().contains("GHOST"));
  }
}

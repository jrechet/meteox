package fr.jrec.meteox.laws.opendata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.opendata.ScrutinExtractionService.ScrutinExtraction;
import fr.jrec.meteox.laws.opendata.ScrutinParser.GroupeVote;
import fr.jrec.meteox.laws.opendata.ScrutinParser.ParsedScrutin;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Non-régression issue #3 : l'agrégation open data reproduit EXACTEMENT les votes vérifiés à la
 * main (seed V2, scrutin 17e n°844), et l'URL officielle du scrutin est reconstruite.
 * Un organeRef inconnu fait échouer l'extraction (Golden Rule : jamais de total faussé).
 * Fixture : fichier réel de l'open data AN (jeu Scrutins 17e), non modifié.
 */
@QuarkusTest
class ScrutinExtractionTest {

  @Inject ScrutinExtractionService service;
  @Inject BlocMapping mapping;

  private InputStream fixture(String name) {
    InputStream in = getClass().getClassLoader().getResourceAsStream("fixtures/scrutins/" + name);
    assertNotNull(in, "fixture " + name + " présente");
    return in;
  }

  @Test
  void aggregates_scrutin_844_exactly_like_the_verified_seed() {
    ScrutinExtraction x = service.extract(fixture("VTANR5L17V844.json"));

    assertEquals(844, x.numero());
    assertEquals(17, x.legislature());
    assertEquals("adopté", x.sortCode());
    assertEquals("https://www.assemblee-nationale.fr/dyn/17/scrutins/844", x.scrutinUrl());

    // Valeurs vérifiées le 2026-07-14 depuis la page officielle du scrutin (seed V2).
    assertEquals(new BlocVotes(1, 160, 0), x.votesByBloc().get("gauche"));
    assertEquals(new BlocVotes(178, 0, 2), x.votesByBloc().get("milieu"));
    assertEquals(new BlocVotes(44, 0, 0), x.votesByBloc().get("droite"));
    assertEquals(new BlocVotes(137, 0, 0), x.votesByBloc().get("extremeDroite"));

    // Ordre des blocs identique à laws.js.
    assertEquals(
        List.of("gauche", "milieu", "droite", "extremeDroite"),
        List.copyOf(x.votesByBloc().keySet()));
  }

  @Test
  void unknown_organe_ref_fails_loudly_instead_of_skewing_totals() {
    ScrutinExtractionService svc = new ScrutinExtractionService();
    svc.parser = new ScrutinParser();
    svc.mapping = mapping;
    ParsedScrutin scrutin =
        new ParsedScrutin(
            999,
            17,
            "test",
            "adopté",
            List.of(
                new GroupeVote("PO845413", 0, 10, 0), // LFI connu → gauche
                new GroupeVote("PO000000", 5, 0, 0))); // inconnu
    UnknownOrganeException ex =
        assertThrows(UnknownOrganeException.class, () -> svc.aggregate(scrutin));
    assertTrue(ex.getMessage().contains("PO000000"));
  }
}

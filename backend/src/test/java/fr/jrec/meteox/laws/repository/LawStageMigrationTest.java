package fr.jrec.meteox.laws.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jrec.meteox.laws.model.Law;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Migration V10 (issue #3) : reconstruction de la table {@code laws} pour rendre {@code date}
 * NULLABLE et ajouter {@code stage}. On vérifie que la reconstruction préserve les données —
 * une loi {@code passed} du seed garde sa vraie date ET ses votes par bloc (les tables enfant
 * référençant {@code laws(id)} ne sont pas cassées) — et qu'on peut désormais insérer une carte
 * {@code upcoming} sans date (NULL) mais avec son étape officielle sourcée.
 */
@QuarkusTest
class LawStageMigrationTest {

  @Inject LawRepository laws;
  @Inject DataSource dataSource;

  @AfterEach
  void cleanup() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'test-mig%'");
    }
  }

  @Test
  void passed_seed_law_keeps_its_real_date_and_votes_after_table_rebuild() {
    // eco-agri-1 est une des 4 lois votées du seed (V2), avec des votes par bloc (table enfant).
    Law law = laws.findById("eco-agri-1").orElseThrow();
    assertEquals("passed", law.status());
    assertEquals("2024-04-04", law.date(), "la vraie date de scrutin est préservée");
    assertNull(law.stage(), "une loi votée ne porte pas d'étape (elle a une date)");
    assertFalse(
        law.votes().isEmpty(),
        "les votes par bloc survivent au DROP/RENAME (foreign_keys off → pas de cascade)");
  }

  @Test
  void upcoming_can_be_stored_with_null_date_and_a_stage() {
    laws.insertUpcoming(
        "test-mig-1",
        "Carte à venir de test",
        "eau",
        "Résumé.",
        "https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17NTESTMIG",
        "fragment",
        "https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17NTESTMIG",
        "fragment",
        "En navette au Sénat");

    Law law = laws.findById("test-mig-1").orElseThrow();
    assertEquals("upcoming", law.status());
    assertNull(law.date(), "la colonne date est désormais nullable pour les cartes à venir");
    assertEquals("En navette au Sénat", law.stage());
    assertTrue(law.published());
  }
}

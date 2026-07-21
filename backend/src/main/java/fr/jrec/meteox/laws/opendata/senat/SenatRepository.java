package fr.jrec.meteox.laws.opendata.senat;

import fr.jrec.meteox.laws.model.BlocVotes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Écriture de la facette Sénat d'une loi : le statut ({@code senat_lois}) et le détail des votes
 * par bloc ({@code scrutins_senat}). Idempotente par loi (remplacement atomique). La LECTURE pour
 * le DTO {@code Law} est faite par {@code LawRepository} (self-contained, comme {@code loadVotes}).
 */
@ApplicationScoped
public class SenatRepository {

  @Inject DataSource dataSource;

  /** Enregistre un scrutin public trouvé : statut + votes par bloc (remplacement atomique). */
  public void saveScrutin(
      String lawId, SenatScrutinRef ref, Map<String, BlocVotes> votesByBloc) {
    inTransaction(
        c -> {
          clear(c, lawId);
          upsertStatut(c, lawId, true, null);
          try (PreparedStatement ins =
              c.prepareStatement(
                  "INSERT INTO scrutins_senat (law_id, session, numero, scrutin_url, scrutin_date,"
                      + " bloc, votes_for, votes_against, votes_abstained)"
                      + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Map.Entry<String, BlocVotes> e : votesByBloc.entrySet()) {
              ins.setString(1, lawId);
              ins.setInt(2, ref.session());
              ins.setInt(3, ref.numero());
              ins.setString(4, ref.scrutinUrl());
              ins.setString(5, ref.scrutinDate());
              ins.setString(6, e.getKey());
              ins.setInt(7, e.getValue().votesFor());
              ins.setInt(8, e.getValue().votesAgainst());
              ins.setInt(9, e.getValue().votesAbstained());
              ins.addBatch();
            }
            ins.executeBatch();
          }
        });
  }

  /** Enregistre l'absence de scrutin public (voté à main levée) : statut seul, motif explicite. */
  public void saveNoPublicScrutin(String lawId, String reason) {
    inTransaction(
        c -> {
          clear(c, lawId);
          upsertStatut(c, lawId, false, reason);
        });
  }

  /** Retire toute facette Sénat d'une loi (statut + votes). */
  public void clear(String lawId) {
    inTransaction(c -> clear(c, lawId));
  }

  private static void clear(Connection c, String lawId) throws SQLException {
    for (String sql :
        new String[] {
          "DELETE FROM scrutins_senat WHERE law_id = ?", "DELETE FROM senat_lois WHERE law_id = ?"
        }) {
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, lawId);
        ps.executeUpdate();
      }
    }
  }

  private static void upsertStatut(Connection c, String lawId, boolean hasScrutin, String reason)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO senat_lois (law_id, has_public_scrutin, reason, updated_at)"
                + " VALUES (?, ?, ?, datetime('now'))")) {
      ps.setString(1, lawId);
      ps.setInt(2, hasScrutin ? 1 : 0);
      ps.setString(3, reason);
      ps.executeUpdate();
    }
  }

  private void inTransaction(SqlTx tx) {
    try (Connection c = dataSource.getConnection()) {
      boolean autoCommit = c.getAutoCommit();
      c.setAutoCommit(false);
      try {
        tx.run(c);
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(autoCommit);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Écriture de la facette Sénat impossible", e);
    }
  }

  @FunctionalInterface
  private interface SqlTx {
    void run(Connection c) throws SQLException;
  }
}

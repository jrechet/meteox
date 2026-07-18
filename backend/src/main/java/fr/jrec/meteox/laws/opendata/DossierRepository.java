package fr.jrec.meteox.laws.opendata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Accès JDBC à la table de staging {@code dossier_candidates} (issue #3, tâche 2). */
@ApplicationScoped
public class DossierRepository {

  @Inject DataSource dataSource;

  /** Un candidat « prochain scrutin » détecté dans l'open data. */
  public record Candidate(
      String uid,
      int legislature,
      String titre,
      String dossierUrl,
      String theme,
      String procedure,
      boolean projetDeLoi,
      boolean terminated,
      String status,
      String promotedLawId) {}

  /** Insère le candidat ou rafraîchit son titre/procédure/état + last_seen. */
  public void upsert(
      String uid,
      int legislature,
      String titre,
      String dossierUrl,
      String theme,
      boolean terminated,
      String procedure,
      boolean projetDeLoi) {
    execute(
        "INSERT INTO dossier_candidates (uid, legislature, titre, dossier_url, theme, terminated,"
            + " procedure, projet_de_loi) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT(uid) DO UPDATE SET titre = excluded.titre,"
            + " terminated = excluded.terminated, procedure = excluded.procedure,"
            + " projet_de_loi = excluded.projet_de_loi, last_seen = datetime('now')",
        ps -> {
          ps.setString(1, uid);
          ps.setInt(2, legislature);
          ps.setString(3, titre);
          ps.setString(4, dossierUrl);
          ps.setString(5, theme);
          ps.setInt(6, terminated ? 1 : 0);
          ps.setString(7, procedure);
          ps.setInt(8, projetDeLoi ? 1 : 0);
        });
  }

  /** L'identifiant de la loi publiée issue de ce candidat, s'il a été promu. */
  public Optional<String> promotedLawId(String uid) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT promoted_law_id FROM dossier_candidates WHERE uid = ? AND status = 'promoted'")) {
      ps.setString(1, uid);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture du candidat impossible : " + uid, e);
    }
  }

  public List<Candidate> listByStatus(String status) {
    var out = new ArrayList<Candidate>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT uid, legislature, titre, dossier_url, theme, procedure, projet_de_loi,"
                    + " terminated, status, promoted_law_id FROM dossier_candidates"
                    + " WHERE status = ? AND terminated = 0"
                    + " ORDER BY projet_de_loi DESC, last_seen DESC")) {
      ps.setString(1, status);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(mapCandidate(rs));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Listing des candidats impossible", e);
    }
    return out;
  }

  public Optional<Candidate> findByUid(String uid) {
    return listByUid(uid);
  }

  public void markPromoted(String uid, String lawId) {
    execute(
        "UPDATE dossier_candidates SET status = 'promoted', promoted_law_id = ? WHERE uid = ?",
        ps -> {
          ps.setString(1, lawId);
          ps.setString(2, uid);
        });
  }

  public void markRejected(String uid) {
    execute(
        "UPDATE dossier_candidates SET status = 'rejected' WHERE uid = ?",
        ps -> ps.setString(1, uid));
  }

  public void markTerminated(String uid) {
    execute(
        "UPDATE dossier_candidates SET terminated = 1 WHERE uid = ?",
        ps -> ps.setString(1, uid));
  }

  /**
   * Réconciliation : retire les candidats NON actionnés (status 'candidate') dont l'uid n'a pas
   * été revu au dernier scan — ils ne matchent plus (résolution d'avant le filtre, dossier
   * devenu hors thème, etc.). Les candidats promus/rejetés sont préservés. À n'appeler qu'après
   * un scan réussi.
   */
  public int deleteUnactionedNotIn(java.util.Set<String> seenUids) {
    try (Connection c = dataSource.getConnection()) {
      if (seenUids.isEmpty()) {
        try (PreparedStatement ps =
            c.prepareStatement("DELETE FROM dossier_candidates WHERE status = 'candidate'")) {
          return ps.executeUpdate();
        }
      }
      String placeholders = String.join(",", java.util.Collections.nCopies(seenUids.size(), "?"));
      try (PreparedStatement ps =
          c.prepareStatement(
              "DELETE FROM dossier_candidates WHERE status = 'candidate' AND uid NOT IN ("
                  + placeholders
                  + ")")) {
        int i = 1;
        for (String uid : seenUids) {
          ps.setString(i++, uid);
        }
        return ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Réconciliation des candidats impossible", e);
    }
  }

  private Optional<Candidate> listByUid(String uid) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT uid, legislature, titre, dossier_url, theme, procedure, projet_de_loi,"
                    + " terminated, status, promoted_law_id FROM dossier_candidates WHERE uid = ?")) {
      ps.setString(1, uid);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapCandidate(rs)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture du candidat impossible : " + uid, e);
    }
  }

  private static Candidate mapCandidate(ResultSet rs) throws SQLException {
    return new Candidate(
        rs.getString("uid"),
        rs.getInt("legislature"),
        rs.getString("titre"),
        rs.getString("dossier_url"),
        rs.getString("theme"),
        rs.getString("procedure"),
        rs.getInt("projet_de_loi") == 1,
        rs.getInt("terminated") == 1,
        rs.getString("status"),
        rs.getString("promoted_law_id"));
  }

  private void execute(String sql, SqlBinder binder) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      binder.bind(ps);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Écriture SQL impossible : " + sql, e);
    }
  }

  @FunctionalInterface
  interface SqlBinder {
    void bind(PreparedStatement ps) throws SQLException;
  }
}

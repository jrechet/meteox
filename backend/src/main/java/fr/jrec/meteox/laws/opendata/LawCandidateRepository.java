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

/** Accès JDBC à la table de staging {@code law_candidates} (issue #3, tâche 3). */
@ApplicationScoped
public class LawCandidateRepository {

  @Inject DataSource dataSource;

  /** Un candidat « loi votée » détecté dans le jeu Scrutins de l'open data. */
  public record Candidate(
      String uid,
      int legislature,
      int numero,
      String titre,
      String dateScrutin,
      String theme,
      String scrutinUrl,
      String votesJson,
      String status,
      String promotedLawId) {}

  /** Insère le candidat ou rafraîchit titre/date/votes + last_seen (les scrutins sont immuables). */
  public void upsert(
      String uid,
      int legislature,
      int numero,
      String titre,
      String dateScrutin,
      String theme,
      String scrutinUrl,
      String votesJson) {
    execute(
        "INSERT INTO law_candidates (uid, legislature, numero, titre, date_scrutin, theme,"
            + " scrutin_url, votes_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT(uid) DO UPDATE SET titre = excluded.titre,"
            + " date_scrutin = excluded.date_scrutin, votes_json = excluded.votes_json,"
            + " last_seen = datetime('now')",
        ps -> {
          ps.setString(1, uid);
          ps.setInt(2, legislature);
          ps.setInt(3, numero);
          ps.setString(4, titre);
          ps.setString(5, dateScrutin);
          ps.setString(6, theme);
          ps.setString(7, scrutinUrl);
          ps.setString(8, votesJson);
        });
  }

  /** Candidats d'un statut donné, du scrutin le plus récent au plus ancien. */
  public List<Candidate> listByStatus(String status) {
    var out = new ArrayList<Candidate>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT uid, legislature, numero, titre, date_scrutin, theme, scrutin_url,"
                    + " votes_json, status, promoted_law_id FROM law_candidates"
                    + " WHERE status = ? ORDER BY date_scrutin DESC")) {
      ps.setString(1, status);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(mapCandidate(rs));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Listing des candidats lois impossible", e);
    }
    return out;
  }

  public Optional<Candidate> findByUid(String uid) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT uid, legislature, numero, titre, date_scrutin, theme, scrutin_url,"
                    + " votes_json, status, promoted_law_id FROM law_candidates WHERE uid = ?")) {
      ps.setString(1, uid);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapCandidate(rs)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture du candidat loi impossible : " + uid, e);
    }
  }

  public void markPromoted(String uid, String lawId) {
    execute(
        "UPDATE law_candidates SET status = 'promoted', promoted_law_id = ? WHERE uid = ?",
        ps -> {
          ps.setString(1, lawId);
          ps.setString(2, uid);
        });
  }

  public void markRejected(String uid) {
    execute(
        "UPDATE law_candidates SET status = 'rejected' WHERE uid = ?",
        ps -> ps.setString(1, uid));
  }

  /**
   * Réconciliation : retire les candidats NON actionnés (status 'candidate') dont l'uid n'a pas
   * été revu au dernier scan — ils ne matchent plus (filtre resserré, seed élargi…). Les
   * candidats promus/rejetés sont préservés (décision humaine). À n'appeler qu'après un scan
   * RÉUSSI de toutes les législatures.
   */
  public int deleteUnactionedNotIn(java.util.Set<String> seenUids) {
    try (Connection c = dataSource.getConnection()) {
      if (seenUids.isEmpty()) {
        try (PreparedStatement ps =
            c.prepareStatement("DELETE FROM law_candidates WHERE status = 'candidate'")) {
          return ps.executeUpdate();
        }
      }
      String placeholders = String.join(",", java.util.Collections.nCopies(seenUids.size(), "?"));
      try (PreparedStatement ps =
          c.prepareStatement(
              "DELETE FROM law_candidates WHERE status = 'candidate' AND uid NOT IN ("
                  + placeholders
                  + ")")) {
        int i = 1;
        for (String uid : seenUids) {
          ps.setString(i++, uid);
        }
        return ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Réconciliation des candidats lois impossible", e);
    }
  }

  private static Candidate mapCandidate(ResultSet rs) throws SQLException {
    return new Candidate(
        rs.getString("uid"),
        rs.getInt("legislature"),
        rs.getInt("numero"),
        rs.getString("titre"),
        rs.getString("date_scrutin"),
        rs.getString("theme"),
        rs.getString("scrutin_url"),
        rs.getString("votes_json"),
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

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
      boolean terminated,
      String status,
      String promotedLawId) {}

  /** Insère le candidat ou rafraîchit son titre/état de terminaison + last_seen. */
  public void upsert(
      String uid, int legislature, String titre, String dossierUrl, String theme, boolean terminated) {
    execute(
        "INSERT INTO dossier_candidates (uid, legislature, titre, dossier_url, theme, terminated)"
            + " VALUES (?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT(uid) DO UPDATE SET titre = excluded.titre,"
            + " terminated = excluded.terminated, last_seen = datetime('now')",
        ps -> {
          ps.setString(1, uid);
          ps.setInt(2, legislature);
          ps.setString(3, titre);
          ps.setString(4, dossierUrl);
          ps.setString(5, theme);
          ps.setInt(6, terminated ? 1 : 0);
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
                "SELECT uid, legislature, titre, dossier_url, theme, terminated, status,"
                    + " promoted_law_id FROM dossier_candidates WHERE status = ? AND terminated = 0"
                    + " ORDER BY last_seen DESC")) {
      ps.setString(1, status);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(
              new Candidate(
                  rs.getString("uid"),
                  rs.getInt("legislature"),
                  rs.getString("titre"),
                  rs.getString("dossier_url"),
                  rs.getString("theme"),
                  rs.getInt("terminated") == 1,
                  rs.getString("status"),
                  rs.getString("promoted_law_id")));
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

  private Optional<Candidate> listByUid(String uid) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT uid, legislature, titre, dossier_url, theme, terminated, status,"
                    + " promoted_law_id FROM dossier_candidates WHERE uid = ?")) {
      ps.setString(1, uid);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.of(
            new Candidate(
                rs.getString("uid"),
                rs.getInt("legislature"),
                rs.getString("titre"),
                rs.getString("dossier_url"),
                rs.getString("theme"),
                rs.getInt("terminated") == 1,
                rs.getString("status"),
                rs.getString("promoted_law_id")));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture du candidat impossible : " + uid, e);
    }
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

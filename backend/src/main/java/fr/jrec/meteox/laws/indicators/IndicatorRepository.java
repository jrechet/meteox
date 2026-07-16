package fr.jrec.meteox.laws.indicators;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Accès JDBC aux scores d'indicateurs (workflow draft → published) et à la piste d'audit.
 * L'API publique ne lit JAMAIS un score draft (acceptance issue #4).
 */
@ApplicationScoped
public class IndicatorRepository {

  @Inject DataSource dataSource;

  public long insertDraft(String lawId, AxisScore axis, String model) {
    String sql =
        """
        INSERT INTO indicator_scores
          (law_id, indicator, score, status, model, justification, citation, confidence)
        VALUES (?, ?, ?, 'draft', ?, ?, ?, ?)
        """;
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, lawId);
      ps.setString(2, axis.indicator());
      ps.setDouble(3, axis.score());
      ps.setString(4, model);
      ps.setString(5, axis.justification());
      ps.setString(6, axis.citation());
      ps.setString(7, axis.confidence());
      ps.executeUpdate();
      try (ResultSet keys = ps.getGeneratedKeys()) {
        keys.next();
        return keys.getLong(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Insertion du score draft impossible (" + lawId + ")", e);
    }
  }

  /** Publie un score draft après validation humaine. Renvoie false si le draft n'existe pas. */
  public boolean publish(long scoreId, String reviewedBy) {
    String sql =
        """
        UPDATE indicator_scores
        SET status = 'published', reviewed_by = ?, reviewed_at = datetime('now')
        WHERE id = ? AND status = 'draft'
        """;
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, reviewedBy);
      ps.setLong(2, scoreId);
      return ps.executeUpdate() == 1;
    } catch (SQLException e) {
      throw new IllegalStateException("Publication du score " + scoreId + " impossible", e);
    }
  }

  public Optional<IndicatorScoreRow> findById(long scoreId) {
    return query("SELECT * FROM indicator_scores WHERE id = ?", ps -> ps.setLong(1, scoreId))
        .stream()
        .findFirst();
  }

  public List<IndicatorScoreRow> findDrafts() {
    return query("SELECT * FROM indicator_scores WHERE status = 'draft' ORDER BY id", ps -> {});
  }

  public List<IndicatorScoreRow> findPublishedByLaw(String lawId) {
    return query(
        """
        SELECT * FROM indicator_scores
        WHERE law_id = ? AND status = 'published'
        ORDER BY CASE indicator
          WHEN 'pesticides' THEN 0 WHEN 'pognonPuissants' THEN 1
          WHEN 'peupleSante' THEN 2 WHEN 'partageEau' THEN 3 END, id
        """,
        ps -> ps.setString(1, lawId));
  }

  public void recordAudit(String action, long scoreId, String lawId, String actor, String detail) {
    String sql =
        "INSERT INTO indicator_audit (action, score_id, law_id, actor, detail) VALUES (?, ?, ?, ?, ?)";
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, action);
      ps.setLong(2, scoreId);
      ps.setString(3, lawId);
      ps.setString(4, actor);
      ps.setString(5, detail);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Écriture de l'audit impossible (" + action + ")", e);
    }
  }

  public List<AuditEntry> findAudit() {
    var entries = new ArrayList<AuditEntry>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement("SELECT * FROM indicator_audit ORDER BY id DESC LIMIT 500");
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        entries.add(
            new AuditEntry(
                rs.getLong("id"),
                rs.getString("action"),
                rs.getLong("score_id"),
                rs.getString("law_id"),
                rs.getString("actor"),
                rs.getString("detail"),
                rs.getString("created_at")));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture de l'audit impossible", e);
    }
    return entries;
  }

  private List<IndicatorScoreRow> query(String sql, SqlBinder binder) {
    var rows = new ArrayList<IndicatorScoreRow>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      binder.bind(ps);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          rows.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture des scores impossible", e);
    }
    return rows;
  }

  private static IndicatorScoreRow mapRow(ResultSet rs) throws SQLException {
    return new IndicatorScoreRow(
        rs.getLong("id"),
        rs.getString("law_id"),
        rs.getString("indicator"),
        asCompactNumber(rs.getDouble("score")),
        rs.getString("status"),
        rs.getString("model"),
        rs.getString("justification"),
        rs.getString("citation"),
        rs.getString("confidence"),
        rs.getString("reviewed_by"),
        rs.getString("reviewed_at"),
        rs.getString("created_at"));
  }

  /** 1.0 → 1 (entier), 1.5 → 1.5 — même sérialisation compacte que LawRepository. */
  private static Number asCompactNumber(double value) {
    return value == Math.rint(value) ? (long) value : value;
  }

  /** Piste d'audit consultable : qui a fait quoi, sur quel score, quand (acceptance issue #4). */
  public record AuditEntry(
      long id,
      String action,
      long scoreId,
      String lawId,
      String actor,
      String detail,
      String createdAt) {}

  @FunctionalInterface
  interface SqlBinder {
    void bind(PreparedStatement ps) throws SQLException;
  }
}

package fr.jrec.meteox.laws.repository;

import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.model.Law;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;

/** Accès JDBC aux lois, scrutins et indicateurs publiés (SQLite). */
@ApplicationScoped
public class LawRepository {

  /** Ordre de restitution identique à src/lib/laws.js. */
  private static final List<String> INDICATOR_ORDER =
      List.of("pesticides", "pognonPuissants", "peupleSante", "partageEau");

  private static final List<String> BLOC_ORDER =
      List.of("gauche", "milieu", "droite", "extremeDroite");

  @Inject DataSource dataSource;

  public List<Law> findPublished() {
    return findLaws("SELECT * FROM laws WHERE published = 1 ORDER BY rowid", null);
  }

  public Optional<Law> findById(String id) {
    return findLaws("SELECT * FROM laws WHERE id = ?", id).stream().findFirst();
  }

  public void unpublish(String id) {
    execute(
        "UPDATE laws SET published = 0, updated_at = datetime('now') WHERE id = ?",
        ps -> ps.setString(1, id));
  }

  /** Votes par bloc actuellement en base pour une loi (ordre de restitution stable). */
  public Map<String, BlocVotes> votesFor(String lawId) {
    try (Connection c = dataSource.getConnection()) {
      return loadVotes(c, lawId);
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture des votes impossible pour " + lawId, e);
    }
  }

  /** Remplace atomiquement les votes d'une loi par les agrégats open data (delete + insert). */
  public void replaceVotes(String lawId, Map<String, BlocVotes> votes) {
    try (Connection c = dataSource.getConnection()) {
      boolean autoCommit = c.getAutoCommit();
      c.setAutoCommit(false);
      try {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM scrutins WHERE law_id = ?")) {
          del.setString(1, lawId);
          del.executeUpdate();
        }
        try (PreparedStatement ins =
            c.prepareStatement(
                "INSERT INTO scrutins (law_id, bloc, votes_for, votes_against, votes_abstained)"
                    + " VALUES (?, ?, ?, ?, ?)")) {
          for (Map.Entry<String, BlocVotes> e : votes.entrySet()) {
            ins.setString(1, lawId);
            ins.setString(2, e.getKey());
            ins.setInt(3, e.getValue().votesFor());
            ins.setInt(4, e.getValue().votesAgainst());
            ins.setInt(5, e.getValue().votesAbstained());
            ins.addBatch();
          }
          ins.executeBatch();
        }
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(autoCommit);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Remplacement des votes impossible pour " + lawId, e);
    }
  }

  /** Journalise une synchronisation open data (issue #3) dans scrutin_syncs. */
  public void recordScrutinSync(
      String lawId,
      int legislature,
      int numero,
      String scrutinUrl,
      boolean changed,
      String oldVotesJson,
      String newVotesJson) {
    execute(
        "INSERT INTO scrutin_syncs (law_id, legislature, numero, scrutin_url, changed,"
            + " old_votes, new_votes) VALUES (?, ?, ?, ?, ?, ?, ?)",
        ps -> {
          ps.setString(1, lawId);
          ps.setInt(2, legislature);
          ps.setInt(3, numero);
          ps.setString(4, scrutinUrl);
          ps.setInt(5, changed ? 1 : 0);
          ps.setString(6, oldVotesJson);
          ps.setString(7, newVotesJson);
        });
  }

  /**
   * Crée une carte {@code upcoming} publiée à partir d'un dossier validé (issue #3, tâche 2).
   * Indicateurs et votes sont vides au départ (aucun scrutin encore) ; check-sources vérifiera
   * l'URL du dossier. Rejette si l'identifiant existe déjà.
   */
  public void insertUpcoming(
      String id,
      String title,
      String category,
      String date,
      String summary,
      String sourceUrl,
      String sourceExpect,
      String textUrl,
      String textExpect) {
    insertLaw(
        "upcoming", id, title, category, date, summary, sourceUrl, sourceExpect, textUrl, textExpect);
  }

  /**
   * Crée une loi votée {@code passed} publiée à partir d'un scrutin validé (issue #3, tâche 3).
   * Les votes par bloc sont posés ensuite via {@link #replaceVotes} ; check-sources vérifiera
   * les URLs scrutin + dossier. Rejette si l'identifiant existe déjà.
   */
  public void insertPassed(
      String id,
      String title,
      String category,
      String date,
      String summary,
      String sourceUrl,
      String sourceExpect,
      String textUrl,
      String textExpect) {
    insertLaw(
        "passed", id, title, category, date, summary, sourceUrl, sourceExpect, textUrl, textExpect);
  }

  /** URLs sources de TOUTES les lois (même dépubliées) — pour ne jamais re-proposer un scrutin déjà couvert. */
  public Set<String> sourceUrls() {
    var out = new HashSet<String>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement("SELECT source_url FROM laws");
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.add(rs.getString(1));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture des URLs sources impossible", e);
    }
    return out;
  }

  private void insertLaw(
      String status,
      String id,
      String title,
      String category,
      String date,
      String summary,
      String sourceUrl,
      String sourceExpect,
      String textUrl,
      String textExpect) {
    execute(
        "INSERT INTO laws (id, title, category, status, date, summary, source_url, source_expect,"
            + " text_url, text_expect, published) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
        ps -> {
          ps.setString(1, id);
          ps.setString(2, title);
          ps.setString(3, category);
          ps.setString(4, status);
          ps.setString(5, date);
          ps.setString(6, summary);
          ps.setString(7, sourceUrl);
          ps.setString(8, sourceExpect);
          ps.setString(9, textUrl);
          ps.setString(10, textExpect);
        });
  }

  public void recordSourceCheck(
      String lawId, String field, String url, Integer httpStatus, boolean ok, String reason) {
    execute(
        "INSERT INTO source_checks (law_id, field, url, http_status, ok, reason) VALUES (?, ?, ?, ?, ?, ?)",
        ps -> {
          ps.setString(1, lawId);
          ps.setString(2, field);
          ps.setString(3, url);
          if (httpStatus == null) {
            ps.setNull(4, java.sql.Types.INTEGER);
          } else {
            ps.setInt(4, httpStatus);
          }
          ps.setInt(5, ok ? 1 : 0);
          ps.setString(6, reason);
        });
  }

  private List<Law> findLaws(String sql, String idParam) {
    var laws = new ArrayList<Law>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      if (idParam != null) {
        ps.setString(1, idParam);
      }
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          laws.add(mapLaw(c, rs));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture des lois impossible", e);
    }
    return laws;
  }

  private Law mapLaw(Connection c, ResultSet rs) throws SQLException {
    String id = rs.getString("id");
    return new Law(
        id,
        rs.getString("title"),
        rs.getString("category"),
        rs.getString("status"),
        rs.getString("date"),
        rs.getString("summary"),
        rs.getString("source_url"),
        rs.getString("source_expect"),
        rs.getString("text_url"),
        rs.getString("text_expect"),
        loadIndicators(c, id),
        loadVotes(c, id),
        rs.getInt("published") == 1);
  }

  private Map<String, Number> loadIndicators(Connection c, String lawId) throws SQLException {
    var raw = new LinkedHashMap<String, Number>();
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT indicator, score FROM indicator_scores WHERE law_id = ? AND status = 'published'")) {
      ps.setString(1, lawId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          raw.put(rs.getString("indicator"), asCompactNumber(rs.getDouble("score")));
        }
      }
    }
    var ordered = new LinkedHashMap<String, Number>();
    for (String key : INDICATOR_ORDER) {
      if (raw.containsKey(key)) {
        ordered.put(key, raw.get(key));
      }
    }
    return ordered;
  }

  private Map<String, BlocVotes> loadVotes(Connection c, String lawId) throws SQLException {
    var raw = new LinkedHashMap<String, BlocVotes>();
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT bloc, votes_for, votes_against, votes_abstained FROM scrutins WHERE law_id = ?")) {
      ps.setString(1, lawId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          raw.put(
              rs.getString("bloc"),
              new BlocVotes(
                  rs.getInt("votes_for"), rs.getInt("votes_against"), rs.getInt("votes_abstained")));
        }
      }
    }
    var ordered = new LinkedHashMap<String, BlocVotes>();
    for (String bloc : BLOC_ORDER) {
      if (raw.containsKey(bloc)) {
        ordered.put(bloc, raw.get(bloc));
      }
    }
    return ordered;
  }

  /** 1.0 → 1 (entier), 1.5 → 1.5 — pour une sérialisation identique à laws.js. */
  private static Number asCompactNumber(double value) {
    return value == Math.rint(value) ? (long) value : value;
  }

  private void execute(String sql, SqlConsumer<PreparedStatement> binder) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      binder.accept(ps);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Écriture SQL impossible : " + sql, e);
    }
  }

  @FunctionalInterface
  interface SqlConsumer<T> {
    void accept(T t) throws SQLException;
  }
}

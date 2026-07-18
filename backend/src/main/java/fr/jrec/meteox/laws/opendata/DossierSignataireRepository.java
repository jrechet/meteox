package fr.jrec.meteox.laws.opendata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

/**
 * Accès JDBC à la table {@code dossier_signataires} (issue #33, sous-issue C). Stocke, pour chaque
 * dossier de loi candidat, son auteur et ses cosignataires résolus (nom + groupe politique). Le
 * NOMBRE de cosignataires — agrégé par groupe — sert de signal d'importance à la relecture ; la
 * table est aussi la matière première de l'analyse réseau ultérieure (index sur {@code acteur_ref}).
 */
@ApplicationScoped
public class DossierSignataireRepository {

  private static final String SIGLE_INCONNU = "?"; // cosignataire dont le groupe n'a pu être résolu

  @Inject DataSource dataSource;

  /** Un signataire résolu d'un dossier. {@code nom}/{@code groupeSigle}/{@code bloc} nuls si non résolus. */
  public record Signataire(String role, String acteurRef, String nom, String groupeSigle, String bloc) {}

  /** Décompte des cosignataires d'un groupe politique (pour l'agrégation par groupe). */
  public record GroupCount(String sigle, String bloc, int count) {}

  /** Agrégat des signataires d'un dossier : auteur, total de cosignataires, décompte par groupe. */
  public record Aggregate(Signataire auteur, int cosignatairesTotal, List<GroupCount> cosignatairesParGroupe) {
    public static Aggregate empty() {
      return new Aggregate(null, 0, List.of());
    }
  }

  /**
   * Remplace TOUS les signataires d'un dossier (DELETE puis INSERT, transactionnel et idempotent) :
   * un re-scan reflète l'état courant sans doublon. Une liste vide efface simplement les anciens.
   */
  public void replaceForDossier(String dossierUid, List<Signataire> signataires) {
    try (Connection c = dataSource.getConnection()) {
      boolean previousAutoCommit = c.getAutoCommit();
      c.setAutoCommit(false);
      try {
        try (PreparedStatement del =
            c.prepareStatement("DELETE FROM dossier_signataires WHERE dossier_uid = ?")) {
          del.setString(1, dossierUid);
          del.executeUpdate();
        }
        if (!signataires.isEmpty()) {
          try (PreparedStatement ins =
              c.prepareStatement(
                  "INSERT INTO dossier_signataires (dossier_uid, role, acteur_ref, nom,"
                      + " groupe_sigle, bloc) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (Signataire s : signataires) {
              ins.setString(1, dossierUid);
              ins.setString(2, s.role());
              ins.setString(3, s.acteurRef());
              ins.setString(4, s.nom());
              ins.setString(5, s.groupeSigle());
              ins.setString(6, s.bloc());
              ins.addBatch();
            }
            ins.executeBatch();
          }
        }
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(previousAutoCommit);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Écriture des signataires du dossier " + dossierUid + " impossible", e);
    }
  }

  /** Signataires d'un dossier (auteur d'abord, puis cosignataires dans l'ordre d'insertion). */
  public List<Signataire> listForDossier(String dossierUid) {
    var out = new ArrayList<Signataire>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT role, acteur_ref, nom, groupe_sigle, bloc FROM dossier_signataires"
                    + " WHERE dossier_uid = ? ORDER BY (role = 'auteur') DESC, id ASC")) {
      ps.setString(1, dossierUid);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(mapSignataire(rs));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture des signataires du dossier " + dossierUid + " impossible", e);
    }
    return out;
  }

  /**
   * Agrège les signataires de plusieurs dossiers en UNE requête (pas de N+1). Rend, par dossier_uid,
   * l'auteur, le total de cosignataires et leur décompte par groupe (trié décroissant). Les dossiers
   * sans signataire sont absents de la map (le caller substitue {@link Aggregate#empty()}).
   */
  public Map<String, Aggregate> aggregate(Collection<String> dossierUids) {
    if (dossierUids.isEmpty()) {
      return Map.of();
    }
    // Ordre stable (auteur avant cosignataires) pour une agrégation déterministe.
    String placeholders = String.join(",", java.util.Collections.nCopies(dossierUids.size(), "?"));
    var byDossier = new HashMap<String, List<Signataire>>();
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT dossier_uid, role, acteur_ref, nom, groupe_sigle, bloc FROM dossier_signataires"
                    + " WHERE dossier_uid IN ("
                    + placeholders
                    + ") ORDER BY dossier_uid, (role = 'auteur') DESC, id ASC")) {
      int i = 1;
      for (String uid : dossierUids) {
        ps.setString(i++, uid);
      }
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          byDossier
              .computeIfAbsent(rs.getString("dossier_uid"), k -> new ArrayList<>())
              .add(mapSignataire(rs));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Agrégation des signataires impossible", e);
    }
    var result = new HashMap<String, Aggregate>();
    byDossier.forEach((uid, rows) -> result.put(uid, aggregateRows(rows)));
    return result;
  }

  /** Réconciliation : supprime les signataires des dossiers qui ne sont plus revus au dernier scan. */
  public int deleteForDossiersNotIn(Set<String> keptDossierUids) {
    try (Connection c = dataSource.getConnection()) {
      if (keptDossierUids.isEmpty()) {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM dossier_signataires")) {
          return ps.executeUpdate();
        }
      }
      String placeholders = String.join(",", java.util.Collections.nCopies(keptDossierUids.size(), "?"));
      try (PreparedStatement ps =
          c.prepareStatement(
              "DELETE FROM dossier_signataires WHERE dossier_uid NOT IN (" + placeholders + ")")) {
        int i = 1;
        for (String uid : keptDossierUids) {
          ps.setString(i++, uid);
        }
        return ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Réconciliation des signataires impossible", e);
    }
  }

  /** Construit l'agrégat d'un dossier depuis ses lignes (auteur en tête, cosignataires ensuite). */
  private static Aggregate aggregateRows(List<Signataire> rows) {
    Signataire auteur = null;
    int total = 0;
    // Décompte par sigle, ordre d'apparition préservé avant tri final.
    var counts = new LinkedHashMap<String, int[]>();
    var blocBySigle = new HashMap<String, String>();
    for (Signataire s : rows) {
      if ("auteur".equals(s.role())) {
        auteur = s;
        continue;
      }
      total++;
      String sigle = s.groupeSigle() != null ? s.groupeSigle() : SIGLE_INCONNU;
      counts.computeIfAbsent(sigle, k -> new int[1])[0]++;
      blocBySigle.putIfAbsent(sigle, s.bloc());
    }
    var parGroupe = new ArrayList<GroupCount>();
    counts.forEach((sigle, n) -> parGroupe.add(new GroupCount(sigle, blocBySigle.get(sigle), n[0])));
    // Signal d'importance : les groupes les plus mobilisés d'abord ; sigle en cas d'égalité (déterministe).
    parGroupe.sort(Comparator.comparingInt(GroupCount::count).reversed().thenComparing(GroupCount::sigle));
    return new Aggregate(auteur, total, List.copyOf(parGroupe));
  }

  private static Signataire mapSignataire(ResultSet rs) throws SQLException {
    return new Signataire(
        rs.getString("role"),
        rs.getString("acteur_ref"),
        rs.getString("nom"),
        rs.getString("groupe_sigle"),
        rs.getString("bloc"));
  }
}

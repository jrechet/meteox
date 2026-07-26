package fr.jrec.meteox.laws.opendata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Analyse réseau des soutiens (issue #33, prolongement) : lit {@code dossier_signataires} pour
 * révéler qui soutient qui — par bloc, par groupe et par personne. Lecture seule, calculée à la
 * demande (le volume — quelques milliers de lignes — tient largement dans des GROUP BY SQLite).
 * Les signataires au groupe non résolu sont EXCLUS des agrégats : mieux vaut un réseau incomplet
 * qu'un réseau faux (Golden Rule).
 */
@ApplicationScoped
public class SupportNetworkRepository {

  @Inject DataSource dataSource;

  /** Poids d'un groupe dans le corpus : présence et rôle d'auteur (nœuds du réseau). */
  public record GroupNode(String sigle, String bloc, int dossiers, int dossiersAuteur) {}

  /** Co-présence de deux groupes parmi les signataires d'un même dossier (arêtes du réseau). */
  public record GroupLink(String a, String b, int dossiers) {}

  /** Cosignatures reçues par les textes d'un bloc auteur, ventilées par bloc cosignataire. */
  public record BlocSupport(String auteurBloc, String cosignataireBloc, int cosignatures) {}

  /**
   * Pont transpartisan : une personne qui cosigne de façon récurrente les textes d'un auteur
   * d'un AUTRE groupe (le « soutien systématique » nominatif).
   */
  public record CrossGroupPair(
      String auteurNom,
      String auteurSigle,
      String auteurBloc,
      String cosignataireNom,
      String cosignataireSigle,
      String cosignataireBloc,
      int dossiers) {}

  /**
   * Taux de résolution des signataires (DoD issue #58 : mesurer avant/après). Compté sur la table
   * {@code dossier_signataires} : combien de dossiers portent un auteur, quelle part des signataires
   * a un groupe politique résolu, et quels sigles restent SANS bloc (trous du mapping organe-blocs).
   */
  public record Coverage(
      int dossiersAvecSignataires,
      int dossiersAvecAuteur,
      int signataires,
      int signatairesAvecGroupe,
      int cosignataires,
      int cosignatairesAvecGroupe,
      List<String> siglesSansBloc) {}

  /** Mesure de couverture de la résolution (auteurs, groupes) sur l'ensemble des signataires stockés. */
  public Coverage coverage() {
    String totals =
        "SELECT COUNT(DISTINCT dossier_uid) AS dossiers,"
            + " COUNT(DISTINCT CASE WHEN role = 'auteur' THEN dossier_uid END) AS dossiers_auteur,"
            + " COUNT(*) AS signataires,"
            + " COUNT(CASE WHEN groupe_sigle IS NOT NULL THEN 1 END) AS sig_groupe,"
            + " COUNT(CASE WHEN role = 'cosignataire' THEN 1 END) AS cosign,"
            + " COUNT(CASE WHEN role = 'cosignataire' AND groupe_sigle IS NOT NULL THEN 1 END) AS cosign_groupe"
            + " FROM dossier_signataires";
    String gaps =
        "SELECT DISTINCT groupe_sigle FROM dossier_signataires"
            + " WHERE groupe_sigle IS NOT NULL AND bloc IS NULL ORDER BY groupe_sigle";
    try (Connection c = dataSource.getConnection()) {
      int dossiers;
      int dossiersAuteur;
      int signataires;
      int sigGroupe;
      int cosign;
      int cosignGroupe;
      try (PreparedStatement ps = c.prepareStatement(totals);
          ResultSet rs = ps.executeQuery()) {
        rs.next();
        dossiers = rs.getInt("dossiers");
        dossiersAuteur = rs.getInt("dossiers_auteur");
        signataires = rs.getInt("signataires");
        sigGroupe = rs.getInt("sig_groupe");
        cosign = rs.getInt("cosign");
        cosignGroupe = rs.getInt("cosign_groupe");
      }
      var siglesSansBloc = new ArrayList<String>();
      try (PreparedStatement ps = c.prepareStatement(gaps);
          ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          siglesSansBloc.add(rs.getString(1));
        }
      }
      return new Coverage(
          dossiers, dossiersAuteur, signataires, sigGroupe, cosign, cosignGroupe,
          List.copyOf(siglesSansBloc));
    } catch (SQLException e) {
      throw new IllegalStateException("Mesure de couverture de la résolution impossible", e);
    }
  }

  /** Groupes présents dans les signataires, triés par présence décroissante. */
  public List<GroupNode> groupNodes() {
    var out = new ArrayList<GroupNode>();
    String sql =
        "SELECT groupe_sigle, MAX(bloc) AS bloc, COUNT(DISTINCT dossier_uid) AS dossiers,"
            + " COUNT(DISTINCT CASE WHEN role = 'auteur' THEN dossier_uid END) AS dossiers_auteur"
            + " FROM dossier_signataires WHERE groupe_sigle IS NOT NULL"
            + " GROUP BY groupe_sigle ORDER BY dossiers DESC, groupe_sigle";
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.add(
            new GroupNode(
                rs.getString("groupe_sigle"),
                rs.getString("bloc"),
                rs.getInt("dossiers"),
                rs.getInt("dossiers_auteur")));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture des nœuds du réseau impossible", e);
    }
    return out;
  }

  /**
   * Paires de groupes co-présents parmi les signataires d'au moins {@code minShared} dossiers
   * (une seule occurrence par dossier et par groupe), triées par poids décroissant.
   */
  public List<GroupLink> groupLinks(int minShared) {
    var out = new ArrayList<GroupLink>();
    String sql =
        "WITH gd AS (SELECT DISTINCT dossier_uid, groupe_sigle FROM dossier_signataires"
            + " WHERE groupe_sigle IS NOT NULL)"
            + " SELECT g1.groupe_sigle AS a, g2.groupe_sigle AS b, COUNT(*) AS dossiers"
            + " FROM gd g1 JOIN gd g2 ON g2.dossier_uid = g1.dossier_uid"
            + " AND g1.groupe_sigle < g2.groupe_sigle"
            + " GROUP BY g1.groupe_sigle, g2.groupe_sigle HAVING COUNT(*) >= ?"
            + " ORDER BY dossiers DESC, a, b";
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, minShared);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new GroupLink(rs.getString("a"), rs.getString("b"), rs.getInt("dossiers")));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture des liens entre groupes impossible", e);
    }
    return out;
  }

  /**
   * Matrice de soutien : pour les textes dont l'auteur appartient à un bloc, le nombre de
   * cosignatures reçues de chaque bloc. Répond à « les textes portés par la gauche sont-ils
   * soutenus au-delà de la gauche ? ».
   */
  public List<BlocSupport> blocSupportMatrix() {
    var out = new ArrayList<BlocSupport>();
    String sql =
        "SELECT a.bloc AS auteur_bloc, s.bloc AS cosignataire_bloc, COUNT(*) AS n"
            + " FROM dossier_signataires a"
            + " JOIN dossier_signataires s ON s.dossier_uid = a.dossier_uid"
            + " AND s.role = 'cosignataire'"
            + " WHERE a.role = 'auteur' AND a.bloc IS NOT NULL AND s.bloc IS NOT NULL"
            + " GROUP BY a.bloc, s.bloc ORDER BY a.bloc, n DESC";
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.add(
            new BlocSupport(
                rs.getString("auteur_bloc"), rs.getString("cosignataire_bloc"), rs.getInt("n")));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture de la matrice de soutien impossible", e);
    }
    return out;
  }

  /**
   * Ponts transpartisans : paires auteur ↔ cosignataire de groupes DIFFÉRENTS, récurrentes sur au
   * moins {@code minDossiers} dossiers. L'auteur « organe » (nom nul) est exclu — on ne relie que
   * des personnes identifiées.
   */
  public List<CrossGroupPair> crossGroupPairs(int minDossiers, int limit) {
    var out = new ArrayList<CrossGroupPair>();
    String sql =
        "SELECT MAX(a.nom) AS auteur_nom, MAX(a.groupe_sigle) AS auteur_sigle,"
            + " MAX(a.bloc) AS auteur_bloc, MAX(c.nom) AS cosignataire_nom,"
            + " MAX(c.groupe_sigle) AS cosignataire_sigle, MAX(c.bloc) AS cosignataire_bloc,"
            + " COUNT(DISTINCT a.dossier_uid) AS dossiers"
            + " FROM dossier_signataires a"
            + " JOIN dossier_signataires c ON c.dossier_uid = a.dossier_uid"
            + " AND c.role = 'cosignataire'"
            + " WHERE a.role = 'auteur' AND a.nom IS NOT NULL AND c.nom IS NOT NULL"
            + " AND a.groupe_sigle IS NOT NULL AND c.groupe_sigle IS NOT NULL"
            + " AND a.groupe_sigle <> c.groupe_sigle"
            + " GROUP BY a.acteur_ref, c.acteur_ref HAVING COUNT(DISTINCT a.dossier_uid) >= ?"
            + " ORDER BY dossiers DESC, auteur_nom, cosignataire_nom LIMIT ?";
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, minDossiers);
      ps.setInt(2, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(
              new CrossGroupPair(
                  rs.getString("auteur_nom"),
                  rs.getString("auteur_sigle"),
                  rs.getString("auteur_bloc"),
                  rs.getString("cosignataire_nom"),
                  rs.getString("cosignataire_sigle"),
                  rs.getString("cosignataire_bloc"),
                  rs.getInt("dossiers")));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Lecture des ponts transpartisans impossible", e);
    }
    return out;
  }
}

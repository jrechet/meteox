package fr.jrec.meteox.laws.opendata;

import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.Signataire;
import fr.jrec.meteox.laws.opendata.acteurs.ActeurReferentiel;
import fr.jrec.meteox.laws.opendata.acteurs.ActeurReferentiel.GroupeAffiliation;
import fr.jrec.meteox.laws.opendata.acteurs.DocumentParser;
import fr.jrec.meteox.laws.opendata.acteurs.DocumentParser.ParsedDocument;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Résout les signataires (auteur + cosignataires) du document de dépôt d'un dossier de loi
 * (issue #33, sous-issue C). BEST-EFFORT par construction : récupère le document via
 * {@link OpenDataDossiers}, l'analyse ({@link DocumentParser}) et résout chaque acteur via le
 * {@link ActeurReferentiel}. Jamais d'exception qui remonterait et casserait le scan.
 *
 * <p>Le contrat distingue DEUX cas que l'ancienne version confondait (perte de données en prod
 * le 2026-07-2x : un run quotidien avec l'open data indisponible a remplacé tous les signataires
 * stockés par des listes vides) :
 *
 * <ul>
 *   <li>{@code Optional.of(liste)} — le document a été lu : résultat FAISANT FOI (même vide) ;
 *   <li>{@code Optional.empty()} — impossible de savoir (document absent du jeu, réseau,
 *       parsing) : l'appelant doit PRÉSERVER les signataires déjà stockés.
 * </ul>
 */
@ApplicationScoped
public class SignataireResolver {

  private static final Logger LOG = Logger.getLogger(SignataireResolver.class);
  private static final String ROLE_AUTEUR = "auteur";
  private static final String ROLE_COSIGNATAIRE = "cosignataire";

  @Inject OpenDataDossiers openData;
  @Inject DocumentParser documentParser;
  @Inject ActeurReferentiel referentiel;

  /**
   * Auteur (personne OU groupe) et cosignataires du document de dépôt, résolus en nom + groupe.
   * L'auteur (s'il existe) est en tête, suivi des cosignataires dans l'ordre du document.
   *
   * @return le résultat faisant foi si le document a été lu (même sans signataire) ;
   *     {@link Optional#empty()} si la résolution a ÉCHOUÉ — l'appelant préserve alors l'existant
   */
  public Optional<List<Signataire>> resolve(int legislature, String depotDocumentRef) {
    if (depotDocumentRef == null || depotDocumentRef.isBlank()) {
      return Optional.empty();
    }
    try {
      Optional<byte[]> raw = openData.documentJson(legislature, depotDocumentRef);
      if (raw.isEmpty()) {
        // Absent du jeu = « je ne sais pas » (jeu partiel, décalage de publication…), PAS
        // « aucun signataire » : ne jamais écraser l'existant sur cette base.
        LOG.debugf("Document de dépôt %s absent du jeu (lég. %d) — signataires existants préservés", depotDocumentRef, legislature);
        return Optional.empty();
      }
      ParsedDocument doc = documentParser.parse(new ByteArrayInputStream(raw.get()));
      var out = new ArrayList<Signataire>();
      author(doc).ifPresent(out::add);
      for (String ref : doc.cosignataireRefs()) {
        out.add(person(ROLE_COSIGNATAIRE, ref));
      }
      return Optional.of(List.copyOf(out));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warnf("Résolution des signataires interrompue (document %s)", depotDocumentRef);
      return Optional.empty();
    } catch (RuntimeException | java.io.IOException e) {
      LOG.warnf("Résolution des signataires du document %s impossible (%s)", depotDocumentRef, e.getMessage());
      return Optional.empty();
    }
  }

  /** Auteur du texte : personne ({@code acteurRef}) ou groupe ({@code organeRef}), selon le document. */
  private Optional<Signataire> author(ParsedDocument doc) {
    if (doc.auteurActeurRef() != null) {
      return Optional.of(person(ROLE_AUTEUR, doc.auteurActeurRef()));
    }
    if (doc.auteurOrganeRef() != null) {
      GroupeAffiliation g = referentiel.groupeDeOrgane(doc.auteurOrganeRef()).orElse(null);
      return Optional.of(
          new Signataire(
              ROLE_AUTEUR,
              doc.auteurOrganeRef(),
              null,
              g != null ? g.sigle() : null,
              g != null ? g.bloc() : null));
    }
    return Optional.empty();
  }

  /** Signataire personne : nom + groupe politique actif si le référentiel les connaît. */
  private Signataire person(String role, String acteurRef) {
    String nom = referentiel.nomDe(acteurRef).orElse(null);
    GroupeAffiliation g = referentiel.groupeDe(acteurRef).orElse(null);
    return new Signataire(role, acteurRef, nom, g != null ? g.sigle() : null, g != null ? g.bloc() : null);
  }
}

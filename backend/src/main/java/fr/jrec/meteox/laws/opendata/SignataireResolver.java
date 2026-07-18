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
 * {@link ActeurReferentiel}. Toute défaillance (document absent, réseau, résolution) donne une
 * liste vide — jamais une exception qui remonterait et casserait le scan des dossiers.
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
   * Rend une liste vide si le document est absent du jeu ou si la résolution échoue (loggé, jamais
   * propagé). L'auteur (s'il existe) est en tête, suivi des cosignataires dans l'ordre du document.
   */
  public List<Signataire> resolve(int legislature, String depotDocumentRef) {
    if (depotDocumentRef == null || depotDocumentRef.isBlank()) {
      return List.of();
    }
    try {
      Optional<byte[]> raw = openData.documentJson(legislature, depotDocumentRef);
      if (raw.isEmpty()) {
        LOG.debugf("Document de dépôt %s absent du jeu (lég. %d) — aucun signataire", depotDocumentRef, legislature);
        return List.of();
      }
      ParsedDocument doc = documentParser.parse(new ByteArrayInputStream(raw.get()));
      var out = new ArrayList<Signataire>();
      author(doc).ifPresent(out::add);
      for (String ref : doc.cosignataireRefs()) {
        out.add(person(ROLE_COSIGNATAIRE, ref));
      }
      return List.copyOf(out);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warnf("Résolution des signataires interrompue (document %s)", depotDocumentRef);
      return List.of();
    } catch (RuntimeException | java.io.IOException e) {
      LOG.warnf("Résolution des signataires du document %s impossible (%s)", depotDocumentRef, e.getMessage());
      return List.of();
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

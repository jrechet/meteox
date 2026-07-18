package fr.jrec.meteox.laws.opendata.acteurs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Parse un document de l'open data AN (jeu {@code dossiers_legislatifs}, nœud {@code document},
 * ex. {@code PIONANR5L17B0517.json}) pour en extraire l'auteur et les cosignataires.
 *
 * <p>Un texte de loi a un auteur soit personne ({@code auteurs.auteur.acteur.acteurRef}, PA…),
 * soit groupe ({@code auteurs.auteur.organe.organeRef}, PO…, aucun auteur personne). Les
 * cosignataires ({@code coSignataires.coSignataire}) peuvent être une liste, un objet unique ou
 * {@code null} ; une cosignature RETIRÉE ({@code dateRetraitCosignature} non null) est exclue.
 */
@ApplicationScoped
public class DocumentParser {

  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Document parsé (données brutes). Exactement un des deux auteurs est renseigné : soit
   * {@code auteurActeurRef} (auteur personne), soit {@code auteurOrganeRef} (texte de groupe).
   * {@code cosignataireRefs} liste les acteurRefs des cosignataires NON retirés, dédoublonnés,
   * dans l'ordre de lecture.
   */
  public record ParsedDocument(
      String auteurActeurRef, String auteurOrganeRef, List<String> cosignataireRefs) {}

  public ParsedDocument parse(InputStream json) {
    try {
      JsonNode d = mapper.readTree(json).path("document");
      if (d.isMissingNode()) {
        throw new IllegalArgumentException("JSON de document invalide : nœud 'document' absent");
      }

      JsonNode auteur = d.path("auteurs").path("auteur");
      String acteurRef = auteur.path("acteur").path("acteurRef").asText(null);
      String organeRef = auteur.path("organe").path("organeRef").asText(null);
      if (acteurRef == null && organeRef == null) {
        throw new IllegalArgumentException("Document sans auteur exploitable (ni acteur, ni organe)");
      }

      return new ParsedDocument(acteurRef, organeRef, cosignataires(d.path("coSignataires")));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Parsing du document impossible : " + e.getMessage(), e);
    }
  }

  /**
   * ActeurRefs des cosignataires non retirés, sans doublons, ordre stable. Le nœud
   * {@code coSignataire} est une liste, un objet unique ou {@code null} (absent).
   */
  private static List<String> cosignataires(JsonNode coSignataires) {
    var refs = new LinkedHashSet<String>();
    for (JsonNode c : asElements(coSignataires.path("coSignataire"))) {
      if (!c.path("dateRetraitCosignature").isNull()
          && !c.path("dateRetraitCosignature").isMissingNode()) {
        continue; // cosignature retirée → exclue
      }
      String ref = c.path("acteur").path("acteurRef").asText(null);
      if (ref != null && !ref.isBlank()) {
        refs.add(ref);
      }
    }
    return List.copyOf(refs);
  }

  /** Normalise un nœud liste / objet unique / absent en une suite d'éléments à itérer. */
  private static Iterable<JsonNode> asElements(JsonNode node) {
    if (node.isArray()) {
      return node;
    }
    var single = new ArrayList<JsonNode>();
    if (node.isObject()) {
      single.add(node);
    }
    return single;
  }
}

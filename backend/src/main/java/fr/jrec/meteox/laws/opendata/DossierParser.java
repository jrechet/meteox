package fr.jrec.meteox.laws.opendata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.Optional;

/**
 * Parse un dossier législatif de l'open data AN (jeu {@code dossiers_legislatifs},
 * nœud {@code dossierParlementaire}) en une représentation neutre. On ne lit que ce qui
 * sert à la détection : identité, titre, et présence d'un acte de promulgation (=> loi
 * promulguée, dossier clos, jamais « à venir »).
 */
@ApplicationScoped
public class DossierParser {

  private final ObjectMapper mapper = new ObjectMapper();

  private static final String CODE_DEPOT = "AN1-DEPOT";

  /** Dossier parsé (données brutes, avant filtrage thématique). */
  public record ParsedDossier(
      String uid,
      int legislature,
      String titre,
      String titreChemin,
      String procedure,
      boolean promulgated,
      String depotDocumentRef) {

    /** URL officielle publique et stable du dossier (l'uid résout ; le slug non). */
    public String url() {
      return "https://www.assemblee-nationale.fr/dyn/" + legislature + "/dossiers/" + uid;
    }
  }

  public ParsedDossier parse(InputStream json) {
    try {
      JsonNode d = mapper.readTree(json).path("dossierParlementaire");
      if (d.isMissingNode()) {
        throw new IllegalArgumentException("JSON de dossier invalide : nœud 'dossierParlementaire' absent");
      }
      String uid = d.path("uid").asText();
      int legislature = d.path("legislature").asInt();
      JsonNode titreNode = d.path("titreDossier");
      String titre = titreNode.path("titre").asText();
      String chemin = titreNode.path("titreChemin").asText(null);
      String procedure = d.path("procedureParlementaire").path("libelle").asText("");
      if (uid.isBlank() || titre.isBlank()) {
        throw new IllegalArgumentException("Dossier sans uid ou titre exploitable");
      }
      JsonNode actes = d.path("actesLegislatifs");
      return new ParsedDossier(
          uid,
          legislature,
          titre,
          chemin,
          procedure,
          hasPromulgation(actes),
          findDepotDocumentRef(actes).orElse(null));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Parsing du dossier impossible : " + e.getMessage(), e);
    }
  }

  /** Vrai si l'arbre des actes contient un acte de promulgation ({@code codeActe} en PROM…). */
  private static boolean hasPromulgation(JsonNode actes) {
    if (actes.isObject()) {
      JsonNode code = actes.get("codeActe");
      if (code != null && code.asText("").startsWith("PROM")) {
        return true;
      }
      for (JsonNode child : actes) {
        if (hasPromulgation(child)) {
          return true;
        }
      }
    } else if (actes.isArray()) {
      for (JsonNode child : actes) {
        if (hasPromulgation(child)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Uid du document de dépôt : parcours en profondeur de l'arbre des actes, on renvoie le
   * {@code texteAssocie} du premier acte {@code codeActe == "AN1-DEPOT"} (1er dépôt à l'Assemblée).
   * Vide si le dossier n'en porte pas — c'est un cas normal (dossier sans texte déposé côté AN).
   */
  private static Optional<String> findDepotDocumentRef(JsonNode actes) {
    if (actes.isObject()) {
      JsonNode code = actes.get("codeActe");
      if (code != null && CODE_DEPOT.equals(code.asText(null))) {
        String texte = actes.path("texteAssocie").asText(null);
        if (texte != null && !texte.isBlank()) {
          return Optional.of(texte);
        }
      }
      for (JsonNode child : actes) {
        Optional<String> found = findDepotDocumentRef(child);
        if (found.isPresent()) {
          return found;
        }
      }
    } else if (actes.isArray()) {
      for (JsonNode child : actes) {
        Optional<String> found = findDepotDocumentRef(child);
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }
}

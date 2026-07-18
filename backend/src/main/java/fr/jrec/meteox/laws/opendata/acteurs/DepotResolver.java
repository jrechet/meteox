package fr.jrec.meteox.laws.opendata.acteurs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.Optional;

/**
 * Relie un dossier législatif (nœud {@code dossierParlementaire}) à son document de dépôt.
 *
 * <p>L'arbre {@code actesLegislatifs} contient un acte {@code codeActe == "AN1-DEPOT"} (1er dépôt
 * à l'Assemblée) dont {@code texteAssocie} est l'uid du DOCUMENT déposé (ex. {@code
 * PIONANR5L17B1399}). On renvoie le premier trouvé. Ne modifie pas le {@code DossierParser}
 * existant (réservé à l'intégration C).
 */
@ApplicationScoped
public class DepotResolver {

  private static final String CODE_DEPOT = "AN1-DEPOT";

  private final ObjectMapper mapper = new ObjectMapper();

  /** Uid du document de dépôt (acte AN1-DEPOT), ou vide si le dossier n'en porte pas. */
  public Optional<String> depotDocumentRef(InputStream dossierJson) {
    try {
      JsonNode d = mapper.readTree(dossierJson).path("dossierParlementaire");
      if (d.isMissingNode()) {
        throw new IllegalArgumentException("JSON de dossier invalide : nœud 'dossierParlementaire' absent");
      }
      return findDepot(d.path("actesLegislatifs"));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Résolution du dépôt impossible : " + e.getMessage(), e);
    }
  }

  /** Parcours en profondeur de l'arbre des actes ; renvoie le texteAssocie du 1er AN1-DEPOT. */
  private static Optional<String> findDepot(JsonNode actes) {
    if (actes.isObject()) {
      JsonNode code = actes.get("codeActe");
      if (code != null && CODE_DEPOT.equals(code.asText(null))) {
        String texte = actes.path("texteAssocie").asText(null);
        if (texte != null && !texte.isBlank()) {
          return Optional.of(texte);
        }
      }
      for (JsonNode child : actes) {
        Optional<String> found = findDepot(child);
        if (found.isPresent()) {
          return found;
        }
      }
    } else if (actes.isArray()) {
      for (JsonNode child : actes) {
        Optional<String> found = findDepot(child);
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }
}

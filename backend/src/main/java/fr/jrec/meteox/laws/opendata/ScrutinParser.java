package fr.jrec.meteox.laws.opendata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse un fichier de scrutin de l'open data AN (jeu Scrutins, ex. VTANR5L17V844.json) en une
 * représentation neutre : numéro, législature, sort, et le décompte des voix par organeRef.
 * On ne lit que {@code decompteVoix} (agrégé officiel du groupe), pas les listes nominatives.
 */
@ApplicationScoped
public class ScrutinParser {

  private final ObjectMapper mapper = new ObjectMapper();

  /** Un groupe et son décompte officiel sur le scrutin. */
  public record GroupeVote(String organeRef, int pour, int contre, int abstentions) {}

  /** Scrutin parsé (données brutes, avant agrégation par bloc). */
  public record ParsedScrutin(
      String uid,
      int numero,
      int legislature,
      String dateScrutin,
      String titre,
      String typeVote,
      String sortCode,
      List<GroupeVote> groupes) {}

  public ParsedScrutin parse(InputStream json) {
    try {
      JsonNode s = mapper.readTree(json).path("scrutin");
      if (s.isMissingNode()) {
        throw new IllegalArgumentException("JSON de scrutin invalide : nœud 'scrutin' absent");
      }
      String uid = s.path("uid").asText();
      int numero = s.path("numero").asInt();
      int legislature = s.path("legislature").asInt();
      String dateScrutin = s.path("dateScrutin").asText();
      String titre = s.path("titre").asText();
      String typeVote = s.path("typeVote").path("libelleTypeVote").asText("");
      String sortCode = s.path("sort").path("code").asText();

      var groupes = new ArrayList<GroupeVote>();
      for (JsonNode g : s.path("ventilationVotes").path("organe").path("groupes").path("groupe")) {
        JsonNode d = g.path("vote").path("decompteVoix");
        groupes.add(
            new GroupeVote(
                g.path("organeRef").asText(),
                asInt(d, "pour"),
                asInt(d, "contre"),
                asInt(d, "abstentions")));
      }
      if (groupes.isEmpty()) {
        throw new IllegalArgumentException(
            "Scrutin n°" + numero + " : aucun groupe dans ventilationVotes");
      }
      return new ParsedScrutin(
          uid, numero, legislature, dateScrutin, titre, typeVote, sortCode, groupes);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Parsing du scrutin impossible : " + e.getMessage(), e);
    }
  }

  /** Les décomptes open data sont des chaînes ("121"). */
  private static int asInt(JsonNode decompte, String field) {
    return Integer.parseInt(decompte.path(field).asText("0").trim());
  }
}

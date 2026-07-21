package fr.jrec.meteox.laws.opendata.senat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse un scrutin public du Sénat (JSON {@code senat.fr/scrutin-public/{session}/scr{session}-{n}.json})
 * en votes nominatifs bruts : {@code {votes:[{matricule, vote}]}}. Le code de vote est {@code p}
 * (pour), {@code c} (contre), {@code a} (abstention) ou {@code n} (n'a pas pris part au vote). Le
 * groupe politique n'est PAS dans ce fichier : il se résout via {@code ODSEN_HISTOGROUPES} à la
 * date du scrutin.
 */
@ApplicationScoped
public class SenatScrutinParser {

  private final ObjectMapper mapper = new ObjectMapper();

  /** Un vote nominatif : matricule du sénateur et code de vote (p/c/a/n). */
  public record SenatVote(String matricule, String vote) {}

  public List<SenatVote> parse(InputStream json) {
    try {
      JsonNode votes = mapper.readTree(json).path("votes");
      if (!votes.isArray()) {
        throw new IllegalArgumentException("JSON de scrutin Sénat invalide : tableau 'votes' absent");
      }
      var out = new ArrayList<SenatVote>();
      for (JsonNode v : votes) {
        String matricule = v.path("matricule").asText(null);
        String vote = v.path("vote").asText(null);
        if (matricule != null && vote != null) {
          out.add(new SenatVote(matricule.trim(), vote.trim()));
        }
      }
      if (out.isEmpty()) {
        throw new IllegalArgumentException("Scrutin Sénat sans aucun vote exploitable");
      }
      return out;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Parsing du scrutin Sénat impossible : " + e.getMessage(), e);
    }
  }
}

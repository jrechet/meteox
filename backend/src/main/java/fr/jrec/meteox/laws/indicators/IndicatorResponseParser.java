package fr.jrec.meteox.laws.indicators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse et valide la sortie JSON d'un backend IA. Rejet automatique (acceptance issue #4) si :
 * JSON invalide, axe manquant, score hors bornes −2..+2 (ou hors pas de 0.5), justification vide,
 * citation vide ou absente du texte source, confiance inconnue.
 */
public final class IndicatorResponseParser {

  public static final List<String> AXES =
      List.of("pesticides", "partageEau", "pognonPuissants", "peupleSante");

  private static final List<String> CONFIDENCES = List.of("faible", "moyenne", "haute");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private IndicatorResponseParser() {}

  public static IndicatorScores parse(String rawModelOutput, LawText law, String modelLabel) {
    JsonNode root = readJson(stripFences(rawModelOutput), law.lawId());
    var axes = new ArrayList<AxisScore>(AXES.size());
    for (String axis : AXES) {
      JsonNode node = root.get(axis);
      if (node == null || !node.isObject()) {
        throw reject(law, "axe manquant ou invalide : " + axis);
      }
      axes.add(parseAxis(axis, node, law));
    }
    return new IndicatorScores(law.lawId(), modelLabel, axes);
  }

  private static AxisScore parseAxis(String axis, JsonNode node, LawText law) {
    JsonNode scoreNode = node.get("score");
    if (scoreNode == null || !scoreNode.isNumber()) {
      throw reject(law, axis + " : score absent ou non numérique");
    }
    double score = scoreNode.asDouble();
    if (score < -2 || score > 2) {
      throw reject(law, axis + " : score hors bornes −2..+2 (" + score + ")");
    }
    if (Math.rint(score * 2) != score * 2) {
      throw reject(law, axis + " : score hors échelle (pas de 0.5) : " + score);
    }
    String justification = node.path("justification").asText("").strip();
    if (justification.isBlank()) {
      throw reject(law, axis + " : justification vide");
    }
    String citation = node.path("citation").asText("").strip();
    if (citation.isBlank()) {
      throw reject(law, axis + " : citation vide — justification sans citation refusée");
    }
    if (!normalize(law.body()).contains(normalize(citation))) {
      throw reject(law, axis + " : la citation n'est pas un extrait exact du texte source");
    }
    String confidence = node.path("confidence").asText("").strip().toLowerCase();
    if (!CONFIDENCES.contains(confidence)) {
      throw reject(law, axis + " : confiance inconnue « " + confidence + " »");
    }
    return new AxisScore(axis, score, justification, citation, confidence);
  }

  private static JsonNode readJson(String payload, String lawId) {
    try {
      JsonNode root = MAPPER.readTree(payload);
      if (root == null || !root.isObject()) {
        throw new IndicatorExtractionException(lawId + " : la sortie IA n'est pas un objet JSON");
      }
      return root;
    } catch (IndicatorExtractionException e) {
      throw e;
    } catch (Exception e) {
      throw new IndicatorExtractionException(lawId + " : sortie IA non parsable en JSON", e);
    }
  }

  /** Tolère un habillage Markdown ```json ... ``` autour de l'objet. */
  private static String stripFences(String raw) {
    String s = raw == null ? "" : raw.strip();
    if (s.startsWith("```")) {
      int firstNewline = s.indexOf('\n');
      int lastFence = s.lastIndexOf("```");
      if (firstNewline >= 0 && lastFence > firstNewline) {
        s = s.substring(firstNewline + 1, lastFence).strip();
      }
    }
    return s;
  }

  /** Normalise espaces, apostrophes et guillemets pour la vérification d'extrait exact. */
  private static String normalize(String text) {
    return text.replace('’', '\'')
        .replace('«', '"')
        .replace('»', '"')
        .replace('“', '"')
        .replace('”', '"')
        .replaceAll("\\s+", " ")
        .strip()
        .toLowerCase();
  }

  private static IndicatorExtractionException reject(LawText law, String detail) {
    return new IndicatorExtractionException("Sortie IA rejetée pour " + law.lawId() + " — " + detail);
  }
}

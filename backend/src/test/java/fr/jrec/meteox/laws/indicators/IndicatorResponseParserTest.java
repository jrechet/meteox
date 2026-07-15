package fr.jrec.meteox.laws.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acceptance issue #4 : sortie JSON validée par schéma — score hors bornes ou justification sans
 * citation exacte du texte source → rejet automatique.
 */
class IndicatorResponseParserTest {

  private static final LawText LAW =
      new LawText(
          "test-loi",
          "Loi de test",
          "Ce texte restreint la fabrication de produits toxiques et sécurise le partage de l'eau.");

  private static String axis(String score, String citation) {
    return """
        {"score": %s, "justification": "Lecture éditoriale de test.",
         "citation": "%s", "confidence": "haute"}
        """
        .formatted(score, citation);
  }

  private static String payload(String pesticidesAxis) {
    String ok = axis("0", "sécurise le partage de l'eau");
    return "{\"pesticides\": %s, \"partageEau\": %s, \"pognonPuissants\": %s, \"peupleSante\": %s}"
        .formatted(pesticidesAxis, ok, ok, ok);
  }

  @Test
  @DisplayName("sortie valide → 4 axes typés, score et confiance restitués")
  void parses_valid_output() {
    IndicatorScores scores =
        IndicatorResponseParser.parse(
            payload(axis("1.5", "restreint la fabrication de produits toxiques")), LAW, "test");
    assertEquals(4, scores.axes().size());
    AxisScore pesticides = scores.axes().get(0);
    assertEquals("pesticides", pesticides.indicator());
    assertEquals(1.5, pesticides.score());
    assertEquals("haute", pesticides.confidence());
  }

  @Test
  @DisplayName("habillage Markdown ```json toléré")
  void tolerates_markdown_fences() {
    String fenced = "```json\n" + payload(axis("1", "produits toxiques")) + "\n```";
    assertEquals(4, IndicatorResponseParser.parse(fenced, LAW, "test").axes().size());
  }

  @Test
  @DisplayName("score hors bornes −2..+2 → rejet automatique")
  void rejects_out_of_bounds_score() {
    var e =
        assertThrows(
            IndicatorExtractionException.class,
            () -> IndicatorResponseParser.parse(payload(axis("3", "produits toxiques")), LAW, "t"));
    assertTrue(e.getMessage().contains("hors bornes"));
  }

  @Test
  @DisplayName("score hors échelle (pas de 0.5) → rejet")
  void rejects_off_scale_score() {
    assertThrows(
        IndicatorExtractionException.class,
        () -> IndicatorResponseParser.parse(payload(axis("0.7", "produits toxiques")), LAW, "t"));
  }

  @Test
  @DisplayName("justification sans citation → rejet automatique")
  void rejects_empty_citation() {
    var e =
        assertThrows(
            IndicatorExtractionException.class,
            () -> IndicatorResponseParser.parse(payload(axis("1", "")), LAW, "t"));
    assertTrue(e.getMessage().contains("citation"));
  }

  @Test
  @DisplayName("citation qui n'est pas un extrait exact du texte source → rejet")
  void rejects_citation_absent_from_source() {
    assertThrows(
        IndicatorExtractionException.class,
        () ->
            IndicatorResponseParser.parse(
                payload(axis("1", "phrase inventée absente du texte")), LAW, "t"));
  }

  @Test
  @DisplayName("axe manquant → rejet")
  void rejects_missing_axis() {
    String threeAxes =
        "{\"pesticides\": %s, \"partageEau\": %s, \"pognonPuissants\": %s}"
            .formatted(
                axis("0", "produits toxiques"),
                axis("0", "produits toxiques"),
                axis("0", "produits toxiques"));
    assertThrows(
        IndicatorExtractionException.class,
        () -> IndicatorResponseParser.parse(threeAxes, LAW, "t"));
  }

  @Test
  @DisplayName("confiance inconnue → rejet")
  void rejects_unknown_confidence() {
    String bad =
        payload(
            """
            {"score": 1, "justification": "x", "citation": "produits toxiques",
             "confidence": "certaine"}
            """);
    assertThrows(
        IndicatorExtractionException.class, () -> IndicatorResponseParser.parse(bad, LAW, "t"));
  }

  @Test
  @DisplayName("sortie non JSON → rejet")
  void rejects_non_json() {
    assertThrows(
        IndicatorExtractionException.class,
        () -> IndicatorResponseParser.parse("Je ne peux pas évaluer ce texte.", LAW, "t"));
  }
}

package fr.jrec.meteox.laws.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * Loi vérifiée, sérialisée dans le même format que src/lib/laws.js (contrat testé par LawsApiTest).
 * Les indicateurs sont des {@link Number} pour restituer les entiers sans décimale (1, pas 1.0).
 */
@JsonPropertyOrder({
  "id", "title", "category", "status", "date", "summary",
  "sourceUrl", "sourceExpect", "textUrl", "textExpect", "indicators", "votes"
})
public record Law(
    String id,
    String title,
    String category,
    String status,
    String date,
    String summary,
    String sourceUrl,
    String sourceExpect,
    String textUrl,
    String textExpect,
    Map<String, Number> indicators,
    Map<String, BlocVotes> votes,
    @JsonIgnore boolean published) {}

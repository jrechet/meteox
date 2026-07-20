package fr.jrec.meteox.laws.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * Loi vérifiée, sérialisée dans le même format que src/lib/laws.js (contrat testé par LawsApiTest).
 * Les indicateurs sont des {@link Number} pour restituer les entiers sans décimale (1, pas 1.0).
 * {@code stage} = étape officielle sourcée des cartes « à venir » ; absente (non sérialisée) pour
 * les lois {@code passed}, qui portent une vraie date de scrutin.
 */
@JsonPropertyOrder({
  "id", "title", "category", "status", "date", "summary",
  "sourceUrl", "sourceExpect", "textUrl", "textExpect", "stage", "indicators", "votes"
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
    @JsonInclude(JsonInclude.Include.NON_NULL) String stage,
    Map<String, Number> indicators,
    Map<String, BlocVotes> votes,
    @JsonIgnore boolean published) {}

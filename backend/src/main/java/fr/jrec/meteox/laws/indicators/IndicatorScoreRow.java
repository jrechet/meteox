package fr.jrec.meteox.laws.indicators;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Ligne de la table indicator_scores exposée par l'API (draft en admin, published en public). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndicatorScoreRow(
    long id,
    String lawId,
    String indicator,
    Number score,
    String status,
    String model,
    String justification,
    String citation,
    String confidence,
    String reviewedBy,
    String reviewedAt,
    String createdAt) {}

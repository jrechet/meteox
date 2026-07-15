package fr.jrec.meteox.laws.indicators;

import java.util.List;

/**
 * Résultat d'une extraction IA : un score justifié par axe. Toujours créé en statut {@code draft}
 * en base — la publication exige une validation humaine (issue #4).
 */
public record IndicatorScores(String lawId, String model, List<AxisScore> axes) {

  public IndicatorScores {
    axes = List.copyOf(axes);
  }
}

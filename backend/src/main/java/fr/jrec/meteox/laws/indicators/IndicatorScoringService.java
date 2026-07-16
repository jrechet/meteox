package fr.jrec.meteox.laws.indicators;

import fr.jrec.meteox.laws.model.Law;
import fr.jrec.meteox.laws.repository.LawRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Orchestration de l'extraction IA : lit le texte source vérifié de la loi, appelle le backend
 * configuré, valide la sortie et enregistre chaque axe en statut {@code draft}. La publication
 * (validation humaine) est une étape distincte — voir AdminIndicatorResource.
 */
@ApplicationScoped
public class IndicatorScoringService {

  private static final Logger LOG = Logger.getLogger(IndicatorScoringService.class);

  @Inject LawRepository laws;
  @Inject IndicatorRepository indicators;
  @Inject IndicatorExtractor extractor;

  /** Extrait et enregistre les scores en draft. Renvoie les identifiants créés. */
  public List<Long> extractDrafts(String lawId, String actor) {
    Law law =
        laws.findById(lawId)
            .orElseThrow(() -> new IllegalArgumentException("Loi inconnue : " + lawId));
    LawText text = new LawText(law.id(), law.title(), law.summary());
    IndicatorScores scores = extractor.extract(text);
    var ids = new ArrayList<Long>(scores.axes().size());
    for (AxisScore axis : scores.axes()) {
      long id = indicators.insertDraft(lawId, axis, scores.model());
      indicators.recordAudit(
          "draft-created",
          id,
          lawId,
          actor,
          "backend=" + extractor.backendName() + " axe=" + axis.indicator() + " score=" + axis.score());
      ids.add(id);
    }
    LOG.infof(
        "Extraction IA %s : %d scores draft créés pour %s (relecture humaine requise)",
        extractor.backendName(), ids.size(), lawId);
    return ids;
  }

  /** Publication après relecture humaine ; trace l'audit (qui, quoi, quand). */
  public IndicatorScoreRow publish(long scoreId, String reviewedBy) {
    if (reviewedBy == null || reviewedBy.isBlank()) {
      throw new IllegalArgumentException("reviewedBy requis : publication sans relecteur refusée");
    }
    IndicatorScoreRow before =
        indicators
            .findById(scoreId)
            .orElseThrow(() -> new IllegalArgumentException("Score inconnu : " + scoreId));
    if (!indicators.publish(scoreId, reviewedBy)) {
      throw new IllegalStateException("Score " + scoreId + " non publiable (déjà publié ?)");
    }
    indicators.recordAudit(
        "published",
        scoreId,
        before.lawId(),
        reviewedBy,
        "axe=" + before.indicator() + " score=" + before.score());
    return indicators.findById(scoreId).orElseThrow();
  }
}

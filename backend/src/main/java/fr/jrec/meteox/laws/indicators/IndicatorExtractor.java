package fr.jrec.meteox.laws.indicators;

/**
 * Contrat commun des backends d'extraction IA (plan lois-sources-fiabilite § 3.2). Trois
 * implémentations interchangeables par configuration ({@code quarkus.laws.ai.backend}) :
 * claude-cli, claude-api, ollama. Les trois passent le même contrat de test
 * (IndicatorExtractorContractTest).
 */
public interface IndicatorExtractor {

  /** Nom du backend tel que déclaré en config (claude-cli | claude-api | ollama). */
  String backendName();

  /**
   * Extrait les scores d'indicateurs du texte source. La sortie est validée par
   * {@link IndicatorResponseParser} : score hors bornes ou justification sans citation exacte du
   * texte source → {@link IndicatorExtractionException}.
   */
  IndicatorScores extract(LawText law);
}

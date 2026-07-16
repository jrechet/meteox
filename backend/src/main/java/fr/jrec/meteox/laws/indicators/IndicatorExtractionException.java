package fr.jrec.meteox.laws.indicators;

/** Sortie IA invalide (JSON malformé, score hors bornes, citation absente…) ou backend en échec. */
public class IndicatorExtractionException extends RuntimeException {

  public IndicatorExtractionException(String message) {
    super(message);
  }

  public IndicatorExtractionException(String message, Throwable cause) {
    super(message, cause);
  }
}

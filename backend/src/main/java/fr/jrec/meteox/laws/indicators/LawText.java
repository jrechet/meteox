package fr.jrec.meteox.laws.indicators;

/**
 * Texte source soumis à l'extraction d'indicateurs : exposé des motifs et/ou résumé vérifié du
 * dossier législatif. Golden Rule (AGENTS.md) : ce texte provient toujours d'une source officielle
 * vérifiée (colonne {@code summary} des lois publiées, elle-même auditée contre les pages AN).
 */
public record LawText(String lawId, String title, String body) {

  public LawText {
    if (lawId == null || lawId.isBlank()) {
      throw new IllegalArgumentException("lawId requis");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("Texte source vide pour " + lawId);
    }
  }
}

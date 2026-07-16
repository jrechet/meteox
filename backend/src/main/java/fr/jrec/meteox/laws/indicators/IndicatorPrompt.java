package fr.jrec.meteox.laws.indicators;

/**
 * Prompt structuré commun aux trois backends. Le vocabulaire des axes suit la ligne éditoriale
 * validée (docs/ligne-editoriale.md) ; les clés JSON restent les identifiants techniques du modèle
 * de données (laws.js / table indicator_scores).
 */
public final class IndicatorPrompt {

  private IndicatorPrompt() {}

  public static final String SYSTEM =
      """
      Tu es l'assistant d'extraction d'indicateurs éditoriaux de Météo Évolution.
      Tu évalues des textes de loi français à l'aune de l'intérêt général (climat, santé,
      partage de l'eau), à partir UNIQUEMENT du texte source fourni. Tu n'inventes jamais
      de faits absents du texte. Tes scores sont une lecture éditoriale assumée ; tes
      citations sont strictement factuelles : chaque citation doit être un extrait EXACT,
      copié mot pour mot, du texte source. Réponds uniquement en JSON valide, sans texte
      autour, sans balises Markdown.
      """;

  /** Échelle : −2 très néfaste · −1 néfaste · 0 neutre/incertain · +1 favorable · +2 très favorable. */
  public static String user(LawText law) {
    return """
        Évalue le texte de loi suivant sur 4 axes. Pour CHAQUE axe, produis :
        - "score" : un nombre entre -2 et 2, par pas de 0.5
          (-2 = très néfaste pour l'intérêt général, 0 = neutre ou effet incertain, +2 = très favorable) ;
        - "justification" : 1 à 3 phrases expliquant le score, fondées sur le texte source ;
        - "citation" : un extrait EXACT du texte source (copié mot pour mot, sans le modifier)
          qui fonde la justification ;
        - "confidence" : "faible", "moyenne" ou "haute".

        Les 4 axes (clés JSON imposées) :
        - "pesticides" : réduction de l'exposition aux pesticides et polluants ;
        - "partageEau" : partage équitable et préservation de la ressource en eau ;
        - "pognonPuissants" : axe « Intérêts privés vs intérêt général » — un score négatif
          signifie que le texte favorise des intérêts privés concentrés au détriment de
          l'intérêt général ;
        - "peupleSante" : axe « Santé & population » — effets sur la santé publique.

        Si le texte ne dit rien sur un axe, score 0, confidence "faible", et cite la partie
        du texte la plus proche du sujet.

        Réponds UNIQUEMENT avec un objet JSON de la forme :
        {"pesticides": {"score": 0, "justification": "...", "citation": "...", "confidence": "moyenne"},
         "partageEau": {...}, "pognonPuissants": {...}, "peupleSante": {...}}

        Titre de la loi : %s

        Texte source (exposé des motifs / résumé du dossier) :
        %s
        """
        .formatted(law.title(), law.body());
  }
}

package fr.jrec.meteox.laws.indicators;

/**
 * Score d'un axe éditorial produit par l'IA, avec sa justification citant le texte source.
 *
 * @param indicator clé technique de l'axe (pesticides, partageEau, pognonPuissants, peupleSante)
 * @param score −2 (très néfaste) .. +2 (très favorable), pas de 0.5
 * @param justification lecture éditoriale expliquant le score
 * @param citation extrait EXACT du texte source qui fonde la justification
 * @param confidence niveau de confiance déclaré par le modèle : faible | moyenne | haute
 */
public record AxisScore(
    String indicator, double score, String justification, String citation, String confidence) {}

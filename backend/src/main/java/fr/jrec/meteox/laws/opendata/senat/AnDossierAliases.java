package fr.jrec.meteox.laws.opendata.senat;

import java.util.Set;

/**
 * Pont uid ↔ slug pour la jointure loi meteox ↔ Dosleg. Le lien AN ({@code loi.url_an}) apparaît
 * sous DEUX formes dans le Dosleg — uid ({@code DLR5L16N46539}) ou slug/titreChemin
 * ({@code proteger_population_risques_pfas}) — alors qu'une loi meteox n'en stocke qu'une. Cette
 * fonction rend, pour une référence donnée, les références équivalentes (l'AUTRE forme du même
 * dossier AN), afin de rattraper une correspondance manquée en direct. Best-effort : rend un
 * ensemble vide quand l'alternative est inconnue.
 */
@FunctionalInterface
public interface AnDossierAliases {

  /** Références AN équivalentes (normalisées) à {@code normalizedRef} pour cette législature. */
  Set<String> aliasesFor(int legislature, String normalizedRef);
}

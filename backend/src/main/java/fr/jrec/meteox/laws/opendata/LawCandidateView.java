package fr.jrec.meteox.laws.opendata;

import fr.jrec.meteox.laws.model.BlocVotes;
import java.util.Map;

/**
 * Candidat « loi votée » tel que servi à la page admin (issue #3, tâche 3) : votes par bloc
 * désérialisés, URL officielle du scrutin — jamais public tant que non promu.
 */
public record LawCandidateView(
    String uid,
    int legislature,
    int numero,
    String titre,
    String dateScrutin,
    String theme,
    String scrutinUrl,
    Map<String, BlocVotes> votes,
    String status,
    String promotedLawId) {}

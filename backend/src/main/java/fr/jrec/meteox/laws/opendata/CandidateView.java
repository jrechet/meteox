package fr.jrec.meteox.laws.opendata;

import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.GroupCount;

/**
 * Vue enrichie d'un candidat pour la page admin (issue #33, sous-issue D) : le candidat brut
 * augmenté de son initiateur et du soutien (cosignataires) agrégé par groupe politique. Le NOMBRE
 * total de cosignataires est le signal d'importance mis en avant à la relecture.
 */
public record CandidateView(
    String uid,
    int legislature,
    String titre,
    String dossierUrl,
    String theme,
    String procedure,
    boolean projetDeLoi,
    boolean terminated,
    String status,
    String promotedLawId,
    Auteur auteur,
    int cosignatairesTotal,
    java.util.List<GroupCount> cosignatairesParGroupe) {

  /** Initiateur du texte : une personne ({@code nom} + groupe) ou un groupe ({@code nom} nul). */
  public record Auteur(String nom, String sigle, String bloc) {}
}

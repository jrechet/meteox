package fr.jrec.meteox.laws.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * Facette « scrutin public au Sénat » d'une loi (extension Sénat, issue #3, tâche 4). Deux formes
 * exclusives, distinguées par {@code hasPublicScrutin} (les champs {@code null} ne sont pas
 * sérialisés) :
 *
 * <ul>
 *   <li>scrutin public trouvé : {@code hasPublicScrutin=true} + {@code session}, {@code numero},
 *       {@code scrutinUrl}, {@code scrutinDate}, {@code votes} (agrégats par bloc) ;
 *   <li>voté à main levée : {@code hasPublicScrutin=false} + {@code reason}. NON un zéro — le Sénat
 *       vote souvent sans scrutin public (cas réel : loi PFAS), il faut le dire explicitement.
 * </ul>
 *
 * <p>Une loi non résolue (aucune correspondance dans l'open data Sénat) ne porte pas de facette du
 * tout ({@code senat} absent de la loi) — voir {@link Law}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "hasPublicScrutin", "session", "numero", "scrutinUrl", "scrutinDate", "reason", "votes"
})
public record SenatFacet(
    boolean hasPublicScrutin,
    Integer session,
    Integer numero,
    String scrutinUrl,
    String scrutinDate,
    String reason,
    Map<String, BlocVotes> votes) {

  /** Scrutin public trouvé : votes agrégés par bloc + traçabilité (page officielle senat.fr). */
  public static SenatFacet withScrutin(
      int session, int numero, String scrutinUrl, String scrutinDate, Map<String, BlocVotes> votes) {
    return new SenatFacet(true, session, numero, scrutinUrl, scrutinDate, null, votes);
  }

  /** Texte voté à main levée : pas de scrutin public au Sénat (explicite, jamais un zéro). */
  public static SenatFacet noPublicScrutin(String reason) {
    return new SenatFacet(false, null, null, null, null, reason, null);
  }
}

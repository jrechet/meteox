package fr.jrec.meteox.laws.opendata.senat;

import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.opendata.senat.SenatScrutinParser.SenatVote;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agrège un scrutin public du Sénat par bloc politique. Croise les votes nominatifs (matricule →
 * p/c/a/n) avec {@link HistoGroupes} (matricule → groupe ODSEN À LA DATE du scrutin) puis avec
 * {@link SenatBlocMapping} (groupe → bloc). Règles (Golden Rule) :
 *
 * <ul>
 *   <li>un votant sans groupe connu à la date, ou un groupe absent de la référence, fait échouer
 *       l'agrégation (jamais de total faussé) ;
 *   <li>les groupes {@code horsBlocs} (RDSE) sont EXCLUS des agrégats, comme les non-inscrits AN ;
 *   <li>les {@code n} (n'ont pas pris part) ne comptent pas.
 * </ul>
 */
@ApplicationScoped
public class SenatScrutinVotes {

  private static final List<String> BLOC_ORDER =
      List.of("gauche", "milieu", "droite", "extremeDroite");

  @Inject SenatScrutinParser parser;
  @Inject SenatBlocMapping mapping;

  /** Votes agrégés par bloc pour le scrutin (session/numéro servent aux messages d'erreur). */
  public Map<String, BlocVotes> aggregate(
      byte[] scrutinJson, LocalDate scrutinDate, HistoGroupes histo, int session, int numero) {
    return aggregate(parser.parse(new ByteArrayInputStream(scrutinJson)), scrutinDate, histo, session, numero);
  }

  Map<String, BlocVotes> aggregate(
      List<SenatVote> votes, LocalDate scrutinDate, HistoGroupes histo, int session, int numero) {
    var acc = new LinkedHashMap<String, int[]>();
    for (String bloc : BLOC_ORDER) {
      acc.put(bloc, new int[3]); // [pour, contre, abstentions]
    }
    for (SenatVote v : votes) {
      int col = voteColumn(v.vote());
      if (col < 0) {
        continue; // 'n' (n'a pas pris part) ou code inattendu : ne compte pas
      }
      String groupe =
          histo
              .groupeAt(v.matricule(), scrutinDate)
              .orElseThrow(
                  () ->
                      new UnknownGroupeSenatException(
                          session,
                          numero,
                          "matricule « " + v.matricule() + " » sans groupe connu à la date du scrutin"));
      if (!mapping.isKnown(groupe)) {
        throw new UnknownGroupeSenatException(
            session, numero, "groupe ODSEN « " + groupe + " » inconnu");
      }
      if (mapping.isHorsBlocs(groupe)) {
        continue; // RDSE : connu, exclu des agrégats par bloc
      }
      Optional<String> bloc = mapping.blocFor(groupe);
      acc.get(bloc.orElseThrow())[col]++;
    }
    var out = new LinkedHashMap<String, BlocVotes>();
    for (String bloc : BLOC_ORDER) {
      int[] a = acc.get(bloc);
      out.put(bloc, new BlocVotes(a[0], a[1], a[2]));
    }
    return out;
  }

  private static int voteColumn(String vote) {
    return switch (vote) {
      case "p" -> 0;
      case "c" -> 1;
      case "a" -> 2;
      default -> -1;
    };
  }
}

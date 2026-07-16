package fr.jrec.meteox.laws.opendata;

import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.opendata.ScrutinParser.GroupeVote;
import fr.jrec.meteox.laws.opendata.ScrutinParser.ParsedScrutin;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extrait d'un scrutin open data AN les votes agrégés par bloc politique (issue #3, tâche
 * extract-scrutins). Le résultat porte l'URL officielle du scrutin {@code /dyn/{lég}/scrutins/{n}}.
 * Un organeRef inconnu fait échouer l'extraction (jamais de total faussé silencieusement).
 */
@ApplicationScoped
public class ScrutinExtractionService {

  private static final List<String> BLOC_ORDER =
      List.of("gauche", "milieu", "droite", "extremeDroite");

  @Inject ScrutinParser parser;
  @Inject BlocMapping mapping;

  /** Résultat d'extraction, prêt à être rapproché d'une loi (votes + provenance). */
  public record ScrutinExtraction(
      int numero,
      int legislature,
      String scrutinUrl,
      String sortCode,
      Map<String, BlocVotes> votesByBloc) {}

  public ScrutinExtraction extract(InputStream scrutinJson) {
    return aggregate(parser.parse(scrutinJson));
  }

  ScrutinExtraction aggregate(ParsedScrutin s) {
    // Accumulateurs mutables locaux, figés en BlocVotes immuables à la fin.
    var acc = new LinkedHashMap<String, int[]>();
    for (String bloc : BLOC_ORDER) {
      acc.put(bloc, new int[3]); // [pour, contre, abstentions]
    }
    for (GroupeVote g : s.groupes()) {
      if (!mapping.isKnown(g.organeRef())) {
        throw new UnknownOrganeException(s.numero(), g.organeRef());
      }
      if (mapping.isHorsBlocs(g.organeRef())) {
        continue; // non-inscrits : connus, exclus des blocs
      }
      int[] a = acc.get(mapping.blocFor(g.organeRef()).orElseThrow());
      a[0] += g.pour();
      a[1] += g.contre();
      a[2] += g.abstentions();
    }
    var votes = new LinkedHashMap<String, BlocVotes>();
    for (String bloc : BLOC_ORDER) {
      int[] a = acc.get(bloc);
      votes.put(bloc, new BlocVotes(a[0], a[1], a[2]));
    }
    String url =
        "https://www.assemblee-nationale.fr/dyn/" + s.legislature() + "/scrutins/" + s.numero();
    return new ScrutinExtraction(s.numero(), s.legislature(), url, s.sortCode(), votes);
  }
}

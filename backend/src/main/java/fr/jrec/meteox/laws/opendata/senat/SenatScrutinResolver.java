package fr.jrec.meteox.laws.opendata.senat;

import fr.jrec.meteox.laws.opendata.senat.DoslegDataset.EnsembleScrutin;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Résout une loi meteox vers son (éventuel) scrutin public au Sénat, via le Dosleg. À partir du
 * {@code text_url} de la loi (dossier AN, forme uid {@code DLR…} ou slug), retrouve la {@code loi}
 * Dosleg par {@code url_an} (avec rattrapage uid↔slug via {@link AnDossierAliases}), remonte la
 * chaîne {@code loi → lecture → lecass → date_seance → scr} et retient les scrutins « sur
 * l'ensemble ». Applique la règle actée : <b>on publie la dernière lecture — la CMP si elle
 * existe</b>.
 */
@ApplicationScoped
public class SenatScrutinResolver {

  private static final Pattern LEGISLATURE = Pattern.compile("/(\\d{1,2})/dossiers/");
  private static final String CMP_PREFIX = "commission mixte paritaire";

  @Inject AnDossierAliases aliases;

  /** Résout la loi (identifiée par son {@code text_url}) contre la vue Dosleg fournie. */
  public SenatResolution resolve(String textUrl, DoslegDataset dataset) {
    if (textUrl == null || textUrl.isBlank()) {
      return SenatResolution.unresolved();
    }
    Optional<DoslegDataset.Loi> loi = findLoi(textUrl, dataset);
    if (loi.isEmpty()) {
      return SenatResolution.unresolved();
    }
    List<EnsembleScrutin> ensemble = dataset.ensembleScrutins(loi.get().loicod());
    if (ensemble.isEmpty()) {
      return SenatResolution.noPublicScrutin();
    }
    EnsembleScrutin chosen = pickLastReading(ensemble);
    return SenatResolution.resolved(
        new SenatScrutinRef(
            chosen.session(),
            chosen.numero(),
            SenatScrutinRef.officialUrl(chosen.session(), chosen.numero()),
            chosen.scrutinDate()));
  }

  private Optional<DoslegDataset.Loi> findLoi(String textUrl, DoslegDataset dataset) {
    String ref = DoslegDataset.normalizeAnRef(textUrl);
    Optional<DoslegDataset.Loi> direct = dataset.loiByAnRef(ref);
    if (direct.isPresent()) {
      return direct;
    }
    // Rattrapage : le Dosleg peut porter l'AUTRE forme du lien AN (uid vs slug).
    int legislature = legislatureOf(textUrl);
    for (String alt : aliases.aliasesFor(legislature, ref)) {
      Optional<DoslegDataset.Loi> viaAlias = dataset.loiByAnRef(DoslegDataset.normalizeAnRef(alt));
      if (viaAlias.isPresent()) {
        return viaAlias;
      }
    }
    return Optional.empty();
  }

  /**
   * Dernière lecture, la CMP si elle existe : parmi les scrutins « sur l'ensemble », on privilégie
   * ceux d'une lecture de commission mixte paritaire ; à défaut tous, on prend le plus récent (date
   * de scrutin décroissante).
   */
  private static EnsembleScrutin pickLastReading(List<EnsembleScrutin> ensemble) {
    List<EnsembleScrutin> cmp = ensemble.stream().filter(SenatScrutinResolver::isCmp).toList();
    List<EnsembleScrutin> pool = cmp.isEmpty() ? ensemble : cmp;
    return pool.stream()
        .max(Comparator.comparing(SenatScrutinResolver::sortableDate))
        .orElseThrow();
  }

  private static boolean isCmp(EnsembleScrutin s) {
    return s.leccom() != null && s.leccom().trim().toLowerCase(Locale.FRENCH).startsWith(CMP_PREFIX);
  }

  /** Date triable ({@code YYYY-MM-DD} se compare lexicographiquement) ; les dates nulles en dernier. */
  private static String sortableDate(EnsembleScrutin s) {
    return s.scrutinDate() == null ? "" : s.scrutinDate();
  }

  private static int legislatureOf(String textUrl) {
    Matcher m = LEGISLATURE.matcher(textUrl);
    return m.find() ? Integer.parseInt(m.group(1)) : 0;
  }
}

package fr.jrec.meteox.laws.opendata.senat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Vue mémoire indexée des sections utiles du dump Dosleg (Sénat) : les {@code loi} porteuses d'un
 * lien AN, et la chaîne {@code lecture → lecass → date_seance → scr} qui relie une loi à ses
 * scrutins publics. Construit par {@link DoslegParser} en streaming (on saute {@code votsen}, 75 %
 * du fichier). Seul l'index {@code loi} par référence AN et le parcours vers les scrutins « sur
 * l'ensemble » sont exposés — le reste des colonnes du dump est ignoré.
 */
public final class DoslegDataset {

  /** Préfixe d'intitulé (scrint) d'un scrutin portant sur l'ensemble d'un texte (§ note 3.2). */
  static final String ENSEMBLE_PREFIX = "sur l'ensemble";

  /** Une loi Dosleg reliée à l'AN (loicod interne, signet dossier Sénat, lien AN brut). */
  public record Loi(String loicod, String signet, String urlAn) {}

  /** Un scrutin « sur l'ensemble » d'un texte, avec l'intitulé de sa lecture (pour la règle CMP). */
  public record EnsembleScrutin(int session, int numero, String scrutinDate, String leccom) {}

  record Lecture(String lecidt, String typleccod, String leccom) {}

  record Lecass(String lecassidt, String lecidt) {}

  record Scrutin(int session, int numero, String scrint, String scrutinDate) {}

  private final Map<String, Loi> loiByRef;
  private final Map<String, List<Lecture>> lecturesByLoicod;
  private final Map<String, List<Lecass>> lecassByLecidt;
  private final Map<String, List<String>> codesByLecassidt; // date_seance.lecidt(=lecassidt) → codes
  private final Map<String, List<Scrutin>> scrutinsByCode;

  DoslegDataset(
      Map<String, Loi> loiByRef,
      Map<String, List<Lecture>> lecturesByLoicod,
      Map<String, List<Lecass>> lecassByLecidt,
      Map<String, List<String>> codesByLecassidt,
      Map<String, List<Scrutin>> scrutinsByCode) {
    this.loiByRef = loiByRef;
    this.lecturesByLoicod = lecturesByLoicod;
    this.lecassByLecidt = lecassByLecidt;
    this.codesByLecassidt = codesByLecassidt;
    this.scrutinsByCode = scrutinsByCode;
  }

  /** Nombre de lois indexées (porteuses d'un lien AN) — pour le log de synchronisation. */
  public int loiCount() {
    return loiByRef.size();
  }

  /**
   * Loi Dosleg dont le lien AN correspond à la référence donnée (dernier segment d'URL, sans
   * {@code .asp}, normalisé). Rend vide si aucune loi ne porte ce lien.
   */
  public Optional<Loi> loiByAnRef(String normalizedRef) {
    return Optional.ofNullable(loiByRef.get(normalizedRef));
  }

  /**
   * Normalise une référence de dossier AN pour la jointure : dernier segment de l'URL, sans
   * l'extension {@code .asp}, en minuscules. Vaut aussi bien pour la forme uid
   * ({@code DLR5L16N46539}) que pour la forme slug ({@code proteger_population_risques_pfas}).
   */
  public static String normalizeAnRef(String urlOrRef) {
    if (urlOrRef == null) {
      return "";
    }
    String s = urlOrRef.trim();
    int slash = s.lastIndexOf('/');
    if (slash >= 0) {
      s = s.substring(slash + 1);
    }
    if (s.endsWith(".asp")) {
      s = s.substring(0, s.length() - 4);
    }
    return s.toLowerCase(Locale.ROOT);
  }

  /**
   * Scrutins « sur l'ensemble » (préfixe d'intitulé) reliés à cette loi, tous types de lecture
   * confondus (1ʳᵉ lecture, CMP…). Remonte {@code lecture → lecass → date_seance → scr}. Dédupliqué
   * par (session, numéro).
   */
  public List<EnsembleScrutin> ensembleScrutins(String loicod) {
    var out = new LinkedHashMap<Long, EnsembleScrutin>();
    for (Lecture lec : lecturesByLoicod.getOrDefault(loicod, List.of())) {
      for (Lecass la : lecassByLecidt.getOrDefault(lec.lecidt(), List.of())) {
        for (String code : codesByLecassidt.getOrDefault(la.lecassidt(), List.of())) {
          for (Scrutin s : scrutinsByCode.getOrDefault(code, List.of())) {
            if (isEnsemble(s.scrint())) {
              long key = (long) s.session() * 100_000 + s.numero();
              out.putIfAbsent(
                  key,
                  new EnsembleScrutin(s.session(), s.numero(), s.scrutinDate(), lec.leccom()));
            }
          }
        }
      }
    }
    return new ArrayList<>(out.values());
  }

  private static boolean isEnsemble(String scrint) {
    return scrint != null
        && scrint.trim().toLowerCase(Locale.FRENCH).startsWith(ENSEMBLE_PREFIX);
  }
}

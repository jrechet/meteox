package fr.jrec.meteox.laws.opendata;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtre thématique commun aux détections open data (dossiers « à venir », corpus des lois
 * votées — issue #3). Mots-clés à frontières de mot, comparés sur un titre normalisé (sans
 * accents, apostrophes typographiques repliées). Volontairement large : le tri fin est fait
 * par la validation humaine, pas par ce filtre grossier.
 */
final class ThemeFilter {

  private static final Pattern THEME =
      Pattern.compile(
          "\\b(eaux?|pesticides?|glyphosate|pfas|climat\\w*|canicul\\w*|energ\\w*|renouvelabl\\w*"
              + "|carbone|agricol\\w*|agricultur\\w*|pesticide\\w*|biodiversit\\w*|environnement\\w*"
              + "|pollution\\w*|ecologi\\w*|nappe\\w*|zones? humides?|littoral\\w*|forets?)\\b");

  private ThemeFilter() {}

  /** Premier mot-clé thématique présent dans le titre (comparaison normalisée), s'il y en a un. */
  static Optional<String> matchTheme(String titre) {
    Matcher m = THEME.matcher(normalize(titre));
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
  }

  /** Minuscules, sans accents, apostrophe typographique (’) repliée en apostrophe simple. */
  static String normalize(String s) {
    String noAccents =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return noAccents.replace('’', '\'').toLowerCase(Locale.FRENCH);
  }
}

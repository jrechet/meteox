package fr.jrec.meteox.laws.opendata;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Référence d'un scrutin AN (législature + numéro), dérivée de l'URL officielle
 * {@code /dyn/{lég}/scrutins/{n}} — le {@code source_url} des lois votées.
 */
public record ScrutinRef(int legislature, int numero) {

  private static final Pattern SCRUTIN_URL = Pattern.compile("/dyn/(\\d+)/scrutins/(\\d+)/?$");

  public static Optional<ScrutinRef> fromUrl(String url) {
    if (url == null) {
      return Optional.empty();
    }
    Matcher m = SCRUTIN_URL.matcher(url);
    return m.find()
        ? Optional.of(new ScrutinRef(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))))
        : Optional.empty();
  }

  /** Nom du fichier de scrutin dans le jeu open data (ex. VTANR5L17V844.json). */
  public String fileName() {
    return "VTANR5L" + legislature + "V" + numero + ".json";
  }
}

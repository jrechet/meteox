package fr.jrec.meteox.laws.opendata.senat;

/**
 * Référence d'un scrutin public du Sénat retenu pour une loi : session (année d'ouverture, ex.
 * 2022 pour la session 2022-2023), numéro, date (ISO {@code YYYY-MM-DD}), et l'URL de la page
 * officielle (traçabilité Golden Rule).
 */
public record SenatScrutinRef(int session, int numero, String scrutinUrl, String scrutinDate) {

  /** URL de la page HTML officielle du scrutin (résultat + analyse par groupe). */
  public static String officialUrl(int session, int numero) {
    return "https://www.senat.fr/scrutin-public/" + session + "/scr" + session + "-" + numero + ".html";
  }
}

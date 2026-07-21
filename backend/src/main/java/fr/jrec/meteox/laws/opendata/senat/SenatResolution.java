package fr.jrec.meteox.laws.opendata.senat;

/**
 * Issue de la résolution d'une loi meteox vers le Sénat via le Dosleg :
 *
 * <ul>
 *   <li>{@code RESOLVED} : la loi a un scrutin public « sur l'ensemble » ({@code scrutin} renseigné) ;
 *   <li>{@code NO_PUBLIC_SCRUTIN} : la loi existe côté Sénat mais n'a AUCUN scrutin public sur
 *       l'ensemble (voté à main levée, cas PFAS) — état assumé, jamais un zéro ;
 *   <li>{@code UNRESOLVED} : aucune loi Dosleg ne correspond (lien AN absent/non apparié).
 * </ul>
 */
public record SenatResolution(Kind kind, SenatScrutinRef scrutin) {

  public enum Kind {
    RESOLVED,
    NO_PUBLIC_SCRUTIN,
    UNRESOLVED
  }

  public static SenatResolution resolved(SenatScrutinRef scrutin) {
    return new SenatResolution(Kind.RESOLVED, scrutin);
  }

  public static SenatResolution noPublicScrutin() {
    return new SenatResolution(Kind.NO_PUBLIC_SCRUTIN, null);
  }

  public static SenatResolution unresolved() {
    return new SenatResolution(Kind.UNRESOLVED, null);
  }
}

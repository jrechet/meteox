package fr.jrec.meteox.laws.opendata;

/**
 * Levée quand un scrutin contient un organeRef absent de la référence organe-blocs.json.
 * Golden Rule (AGENTS.md) : un groupe non référencé ne doit jamais être silencieusement ignoré —
 * il fausserait les totaux par bloc. Il faut compléter la référence avant de publier ce scrutin.
 */
public class UnknownOrganeException extends RuntimeException {
  public UnknownOrganeException(int numero, String organeRef) {
    super(
        "Scrutin n°"
            + numero
            + " : organeRef inconnu « "
            + organeRef
            + " » — compléter reference/organe-blocs.json avant agrégation.");
  }
}

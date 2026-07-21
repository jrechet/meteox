package fr.jrec.meteox.laws.opendata.senat;

/**
 * Levée quand un scrutin du Sénat contient un vote dont le groupe (code ODSEN, à la date du
 * scrutin) est absent de la référence {@code senat-groupe-blocs.json}, ou dont le matricule n'a
 * aucune appartenance de groupe connue à cette date. Golden Rule (AGENTS.md) : un groupe ou un
 * votant non résolu ne doit jamais être silencieusement ignoré — il fausserait les totaux par bloc.
 */
public class UnknownGroupeSenatException extends RuntimeException {
  public UnknownGroupeSenatException(int session, int numero, String detail) {
    super(
        "Scrutin Sénat "
            + session
            + "-"
            + numero
            + " : "
            + detail
            + " — compléter reference/senat-groupe-blocs.json (ou vérifier ODSEN_HISTOGROUPES)"
            + " avant agrégation.");
  }
}

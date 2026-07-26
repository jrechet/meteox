package fr.jrec.meteox.laws.opendata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * Résolution de la législature d'un uid de document/dépôt AN. Un dossier de la 17e législature peut
 * porter un dépôt hérité d'une législature antérieure ({@code …L16B…}) : le document ne vit alors PAS
 * dans le zip de la 17e mais dans celui de SA propre législature. On lit le token {@code L<NN>B} de
 * l'uid pour aller chercher le document au bon endroit (fiabilisation issue #58, cause « lég. absente »).
 */
class SignataireResolverTest {

  @Test
  void legislature_lue_dans_l_uid_de_document() {
    assertEquals(OptionalInt.of(17), SignataireResolver.legislatureOfRef("PIONANR5L17B0517"));
    assertEquals(OptionalInt.of(17), SignataireResolver.legislatureOfRef("PRJLANR5L17B2632"));
    // Dépôt hérité d'une législature antérieure : le token L<NN> reflète la VRAIE législature du doc.
    assertEquals(OptionalInt.of(16), SignataireResolver.legislatureOfRef("PIONANR5L16B0413"));
    assertEquals(OptionalInt.of(15), SignataireResolver.legislatureOfRef("PIONANR5L15B1173"));
  }

  @Test
  void ref_sans_token_de_legislature_exploitable_renvoie_vide() {
    assertTrue(SignataireResolver.legislatureOfRef(null).isEmpty());
    assertTrue(SignataireResolver.legislatureOfRef("").isEmpty());
    assertTrue(SignataireResolver.legislatureOfRef("DOCUMENT-SANS-TOKEN").isEmpty());
  }

  @Test
  void le_token_de_legislature_ne_confond_pas_un_autre_L_du_prefixe() {
    // Le préfixe « …ANR5L17B… » : seul le L collé à un B (numéro de dépôt) est la législature.
    OptionalInt leg = SignataireResolver.legislatureOfRef("PIONANR5L17B0517");
    assertFalse(leg.isEmpty());
    assertEquals(17, leg.getAsInt());
  }
}

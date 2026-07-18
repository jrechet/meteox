package fr.jrec.meteox.laws.opendata.acteurs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jrec.meteox.laws.opendata.acteurs.DocumentParser.ParsedDocument;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unitaire pur (module B, issue #35) : extraction de l'auteur et des cosignataires d'un document
 * open data AN. Fixtures = fichiers réels du jeu Dossiers_Legislatifs (17e législature), non
 * modifiés — auteur personne + cosignataires, retrait de cosignature, et texte de groupe.
 */
class DocumentParserTest {

  private final DocumentParser parser = new DocumentParser();

  private InputStream fixture(String name) {
    InputStream in =
        getClass().getClassLoader().getResourceAsStream("fixtures/documents/" + name);
    assertNotNull(in, "fixture " + name + " présente");
    return in;
  }

  @Test
  void parses_person_author_and_all_active_cosignatories() {
    // B0517 : auteur PA841853, 65 cosignataires, aucun retrait (vérifié sur l'open data 17e).
    ParsedDocument doc = parser.parse(fixture("PIONANR5L17B0517.json"));

    assertEquals("PA841853", doc.auteurActeurRef());
    assertNull(doc.auteurOrganeRef(), "un auteur personne n'a pas d'organeRef");
    assertEquals(65, doc.cosignataireRefs().size());
    assertTrue(doc.cosignataireRefs().contains("PA719930"));
    assertTrue(doc.cosignataireRefs().contains("PA608264"));
    // Ordre stable : premier cosignataire du fichier en tête.
    assertEquals("PA719930", doc.cosignataireRefs().get(0));
    // Pas de doublons (LinkedHashSet interne).
    assertEquals(
        doc.cosignataireRefs().size(),
        doc.cosignataireRefs().stream().distinct().count());
  }

  @Test
  void excludes_a_withdrawn_cosignatory() {
    // B0413 : auteur PA346782, 11 cosignataires dont 1 retrait (PA332523, dateRetraitCosignature
    // non null le 2025-02-17) → 10 cosignataires actifs, PA332523 exclu.
    ParsedDocument doc = parser.parse(fixture("PIONANR5L17B0413.json"));

    assertEquals("PA346782", doc.auteurActeurRef());
    assertEquals(10, doc.cosignataireRefs().size());
    assertFalse(
        doc.cosignataireRefs().contains("PA332523"),
        "une cosignature retirée (dateRetraitCosignature non null) est exclue");
    assertTrue(doc.cosignataireRefs().contains("PA642847"));
    assertTrue(doc.cosignataireRefs().contains("PA1327"));
  }

  @Test
  void group_text_has_organe_author_and_no_cosignatories() {
    // B0147 : texte déposé par un groupe (organeRef PO838901), coSignataires = null.
    ParsedDocument doc = parser.parse(fixture("PIONANR5L17B0147.json"));

    assertNull(doc.auteurActeurRef(), "un texte de groupe n'a pas d'auteur personne");
    assertEquals("PO838901", doc.auteurOrganeRef());
    assertTrue(doc.cosignataireRefs().isEmpty());
  }

  @Test
  void handles_a_single_cosignatory_object_not_only_a_list() {
    // coSignataire peut être un objet unique (pas une liste) : on doit le lire quand même.
    String json =
        """
        {"document":{
          "auteurs":{"auteur":{"acteur":{"acteurRef":"PA0001"}}},
          "coSignataires":{"coSignataire":{"acteur":{"acteurRef":"PA0002"},
            "dateRetraitCosignature":null}}
        }}
        """;
    ParsedDocument doc = parser.parse(stream(json));

    assertEquals("PA0001", doc.auteurActeurRef());
    assertEquals(1, doc.cosignataireRefs().size());
    assertEquals("PA0002", doc.cosignataireRefs().get(0));
  }

  @Test
  void rejects_a_document_without_any_author() {
    String json = "{\"document\":{\"coSignataires\":null}}";
    assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(json)));
  }

  @Test
  void rejects_json_without_document_node() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse(stream("{\"autre\":1}")));
  }

  private static InputStream stream(String json) {
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }
}

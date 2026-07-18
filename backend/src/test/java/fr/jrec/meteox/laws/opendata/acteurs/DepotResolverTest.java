package fr.jrec.meteox.laws.opendata.acteurs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unitaire pur (module B, issue #35) : résolution du document de dépôt (acte AN1-DEPOT) d'un
 * dossier législatif. Fixture réelle {@code DLR5L17N52071} (AN1-DEPOT → {@code PIONANR5L17B1399}) ;
 * fixture dérivée sans acte de dépôt pour le cas vide.
 */
class DepotResolverTest {

  private final DepotResolver resolver = new DepotResolver();

  private InputStream fixture(String name) {
    InputStream in =
        getClass().getClassLoader().getResourceAsStream("fixtures/dossiers-depot/" + name);
    assertNotNull(in, "fixture " + name + " présente");
    return in;
  }

  @Test
  void resolves_depot_document_ref_from_real_dossier() {
    Optional<String> ref = resolver.depotDocumentRef(fixture("DLR5L17N52071.json"));

    assertTrue(ref.isPresent());
    assertEquals("PIONANR5L17B1399", ref.get());
  }

  @Test
  void returns_empty_when_dossier_has_no_depot_act() {
    // Fixture dérivée : arbre d'actes valide (AN1, AN1-COM) mais aucun AN1-DEPOT.
    Optional<String> ref = resolver.depotDocumentRef(fixture("dossier-sans-depot.json"));

    assertTrue(ref.isEmpty());
  }

  @Test
  void rejects_json_without_dossier_node() {
    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.depotDocumentRef(stream("{\"autre\":1}")));
  }

  private static InputStream stream(String json) {
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }
}

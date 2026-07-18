package fr.jrec.meteox.laws.opendata.acteurs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import fr.jrec.meteox.laws.opendata.acteurs.ActeurParser.ParsedActeur;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Contrat de {@link ActeurParser} : identité + organeRef du groupe ACTIF (dateFin nulle). Le nœud
 * {@code mandat} peut être un OBJET (un seul mandat) ou une LISTE — les deux formes sont couvertes,
 * sur fixtures réelles AMO30 et sur JSON minimal en ligne.
 */
class ActeurParserTest {

  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/acteurs");
  private final ActeurParser parser = new ActeurParser();

  private ParsedActeur parseFixture(String uid) throws Exception {
    try (InputStream in = Files.newInputStream(FIXTURES.resolve(uid + ".json"))) {
      return parser.parse(in);
    }
  }

  private ParsedActeur parseJson(String json) {
    return parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void parses_real_actor_with_active_group_mandate() throws Exception {
    // Fixture réelle : Stéphane Viry, mandat GP actif → LIOT (PO845485). mandat = LISTE.
    ParsedActeur a = parseFixture("PA721474");

    assertEquals("PA721474", a.uid());
    assertEquals("Stéphane", a.prenom());
    assertEquals("Viry", a.nom());
    assertEquals("PO845485", a.groupeOrganeRef());
  }

  @Test
  void returns_null_group_ref_when_no_active_group_mandate() throws Exception {
    // Fixture réelle : ancien député, tous ses mandats GP ont une dateFin → aucun groupe actif.
    ParsedActeur a = parseFixture("PA1001");

    assertEquals("PA1001", a.uid());
    assertEquals("Marc-Philippe", a.prenom());
    assertEquals("Daubresse", a.nom());
    assertNull(a.groupeOrganeRef());
  }

  @Test
  void resolves_group_ref_even_when_organe_is_absent_from_bloc_mapping() throws Exception {
    // Fixture réelle : Éric Ciotti, groupe actif PO872880 (UDDPLR), non présent dans organe-blocs.
    ParsedActeur a = parseFixture("PA330240");

    assertEquals("Éric", a.prenom());
    assertEquals("Ciotti", a.nom());
    assertEquals("PO872880", a.groupeOrganeRef());
  }

  @Test
  void handles_single_mandate_as_object() {
    // Contrat : mandat est un OBJET (un seul mandat), pas une liste.
    String json =
        """
        {"acteur":{
          "uid":{"#text":"PA9001"},
          "etatCivil":{"ident":{"prenom":"Jean","nom":"Test"}},
          "mandats":{"mandat":{"typeOrgane":"GP","dateFin":null,"organes":{"organeRef":"PO845485"}}}
        }}""";

    ParsedActeur a = parseJson(json);

    assertEquals("PA9001", a.uid());
    assertEquals("PO845485", a.groupeOrganeRef());
  }

  @Test
  void handles_multiple_mandates_as_list_and_picks_active_group() {
    // Contrat : mandat est une LISTE ; on retient le GP dont dateFin est nulle (mandat courant).
    String json =
        """
        {"acteur":{
          "uid":{"#text":"PA9002"},
          "etatCivil":{"ident":{"prenom":"Marie","nom":"Exemple"}},
          "mandats":{"mandat":[
            {"typeOrgane":"COMPER","dateFin":null,"organes":{"organeRef":"PO111"}},
            {"typeOrgane":"GP","dateFin":"2022-06-21","organes":{"organeRef":"PO730934"}},
            {"typeOrgane":"GP","dateFin":null,"organes":{"organeRef":"PO845419"}}
          ]}
        }}""";

    ParsedActeur a = parseJson(json);

    assertEquals("PA9002", a.uid());
    assertEquals("PO845419", a.groupeOrganeRef());
  }

  @Test
  void returns_null_when_only_ended_group_mandate_present() {
    // Un mandat GP clos (dateFin non nulle) ne compte pas comme rattachement actif.
    String json =
        """
        {"acteur":{
          "uid":{"#text":"PA9003"},
          "etatCivil":{"ident":{"prenom":"Paul","nom":"Ancien"}},
          "mandats":{"mandat":{"typeOrgane":"GP","dateFin":"2024-06-09","organes":{"organeRef":"PO800508"}}}
        }}""";

    assertNull(parseJson(json).groupeOrganeRef());
  }
}

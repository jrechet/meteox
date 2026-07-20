package fr.jrec.meteox.laws.opendata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jrec.meteox.laws.opendata.DossierParser.ParsedDossier;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Extraction de l'ÉTAPE courante d'un dossier depuis {@code actesLegislatifs} (issue #3). Le
 * mapping {@code codeActe → libellé français} est bâti sur les VRAIES valeurs des fixtures de
 * dossiers AN (17e législature) ; l'étape est toujours sourcée (l'acte daté le plus avancé de
 * l'arbre), jamais fabriquée. Un {@code codeActe} inconnu ou un dossier sans acte daté retombe
 * sur le repli neutre « En cours d'examen ».
 */
class DossierParserTest {

  private final DossierParser parser = new DossierParser();

  private ParsedDossier parseFixture(String path) {
    try (InputStream in = getClass().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Fixture absente : " + path);
      }
      return parser.parse(in);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private String stageOf(String json) {
    return parser
        .parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
        .stage();
  }

  // --- Étapes sourcées depuis des fixtures RÉELLES (codeActe observés dans le jeu dossiers AN) ---

  @Test
  void dossier_en_commission_au_fond() {
    // DLR5L17N53637 : acte le plus avancé = AN1-COM-FOND-SAISIE (renvoi en commission au fond).
    assertEquals("En commission", parseFixture("/fixtures/dossiers/DLR5L17N53637.json").stage());
  }

  @Test
  void dossier_a_peine_depose() {
    // DLR5L17N54623 : unique acte daté = ANLUNI-DEPOT (1er dépôt d'une initiative).
    assertEquals("Déposé", parseFixture("/fixtures/dossiers/DLR5L17N54623.json").stage());
  }

  @Test
  void dossier_en_commission_mixte_paritaire() {
    // DLR5L17N54085 : acte le plus récent = CMP-DEC (décision de la CMP) → phase CMP.
    assertEquals(
        "En commission mixte paritaire",
        parseFixture("/fixtures/dossiers/DLR5L17N54085.json").stage());
  }

  @Test
  void dossier_promulgue_est_marque_promulguee() {
    // DLR5L17N51352 : acte le plus avancé = PROM-PUB. (Un dossier promulgué est de toute façon
    // exclu des cartes « à venir » ; on vérifie juste que l'étape reste honnête.)
    ParsedDossier d = parseFixture("/fixtures/dossiers/DLR5L17N51352.json");
    assertTrue(d.promulgated());
    assertEquals("Promulguée", d.stage());
  }

  @Test
  void dossier_sans_acte_date_retombe_sur_le_repli() {
    // dossier-sans-depot : arbre AN1 → AN1-COM sans AUCUN dateActe → repli neutre, jamais d'invention.
    assertEquals(
        DossierParser.STAGE_FALLBACK,
        parseFixture("/fixtures/dossiers-depot/dossier-sans-depot.json").stage());
    assertEquals("En cours d'examen", DossierParser.STAGE_FALLBACK);
  }

  // --- Branches chambre/lecture : arbre minimal, codeActe RÉELS de l'AN (séance en 1re / navette) ---

  @Test
  void premiere_lecture_a_l_assemblee_quand_la_seance_est_l_acte_le_plus_avance() {
    String json =
        """
        {"dossierParlementaire":{"uid":"DLTEST1","legislature":17,
         "titreDossier":{"titre":"Texte de test 1re lecture"},
         "procedureParlementaire":{"libelle":"Proposition de loi ordinaire"},
         "actesLegislatifs":{"acteLegislatif":{
           "codeActe":"AN1","libelleActe":{"nomCanonique":"1ère lecture (1ère assemblée saisie)"},
           "dateActe":null,"actesLegislatifs":{"acteLegislatif":{
             "codeActe":"AN1-DEBATS-SEANCE","dateActe":"2025-04-07T00:00:00.000+02:00",
             "actesLegislatifs":null}}}}}}
        """;
    assertEquals("En 1re lecture à l'Assemblée", stageOf(json));
  }

  @Test
  void navette_au_senat_quand_la_seconde_chambre_saisie_est_le_senat() {
    // Deux phases : Sénat 1re assemblée saisie (séance), puis AN navette. L'acte le plus récent
    // est au Sénat en 2ème assemblée saisie → « En navette au Sénat ».
    String json =
        """
        {"dossierParlementaire":{"uid":"DLTEST2","legislature":17,
         "titreDossier":{"titre":"Texte de test navette"},
         "procedureParlementaire":{"libelle":"Proposition de loi ordinaire"},
         "actesLegislatifs":{"acteLegislatif":[
           {"codeActe":"AN1","libelleActe":{"nomCanonique":"1ère lecture (1ère assemblée saisie)"},
            "dateActe":null,"actesLegislatifs":{"acteLegislatif":{
              "codeActe":"AN1-DEBATS-DEC","dateActe":"2025-03-25T00:00:00.000+01:00",
              "actesLegislatifs":null}}},
           {"codeActe":"SN1","libelleActe":{"nomCanonique":"1ère lecture (2ème assemblée saisie)"},
            "dateActe":null,"actesLegislatifs":{"acteLegislatif":{
              "codeActe":"SN1-DEBATS-SEANCE","dateActe":"2025-05-06T00:00:00.000+02:00",
              "actesLegislatifs":null}}}]}}}
        """;
    assertEquals("En navette au Sénat", stageOf(json));
  }

  @Test
  void depot_prime_sur_la_chambre_quand_c_est_le_seul_acte() {
    // Un projet de loi juste déposé (dépôt + étude d'impact le même jour) : l'étape reste « Déposé ».
    String json =
        """
        {"dossierParlementaire":{"uid":"DLTEST3","legislature":17,
         "titreDossier":{"titre":"Projet de test à peine déposé"},
         "procedureParlementaire":{"libelle":"Projet de loi ordinaire"},
         "actesLegislatifs":{"acteLegislatif":{
           "codeActe":"AN1","libelleActe":{"nomCanonique":"1ère lecture (1ère assemblée saisie)"},
           "dateActe":null,"actesLegislatifs":{"acteLegislatif":[
             {"codeActe":"AN1-DEPOT","dateActe":"2026-04-08T00:00:00.000+02:00","actesLegislatifs":null},
             {"codeActe":"AN1-ETI","dateActe":"2026-04-08T00:00:00.000+02:00","actesLegislatifs":null}]}}}}}
        """;
    assertEquals("Déposé", stageOf(json));
  }

  @Test
  void depot_conserve_le_document_de_reference_en_plus_de_l_etape() {
    // Régression : l'ajout de l'étape ne casse pas l'extraction du texte déposé (AN1-DEPOT).
    ParsedDossier d = parseFixture("/fixtures/dossiers-depot/DLR5L17N52071.json");
    assertEquals("PIONANR5L17B1399", d.depotDocumentRef());
    assertEquals("En commission", d.stage());
  }
}

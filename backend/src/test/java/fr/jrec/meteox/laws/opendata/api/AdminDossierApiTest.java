package fr.jrec.meteox.laws.opendata.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasItems;

import fr.jrec.meteox.laws.opendata.DossierRepository;
import fr.jrec.meteox.laws.opendata.DossierSignataireRepository;
import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.Signataire;
import fr.jrec.meteox.laws.opendata.DossierSyncService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Couverture de TOUTES les actions admin dossiers COMME un navigateur : chaque requête porte
 * l'en-tête {@code Origin} (la page admin est servie par le backend, donc un POST navigateur
 * envoie {@code Origin: https://jrec.fr}). Ce test aurait attrapé le 403 CORS du bouton
 * « Synchroniser ». Couvre aussi l'authentification et le refus d'une origine inconnue.
 */
@QuarkusTest
class AdminDossierApiTest {

  private static final String ADMIN = "test-admin-token"; // %test.meteox.admin.token
  private static final String ORIGIN = "https://jrec.fr"; // origine de la page admin
  private static final String CAND = "DLR5L17N99999";

  @Inject DossierRepository candidates;
  @Inject DossierSignataireRepository signataires;
  @Inject DossierSyncService syncService;
  @Inject DataSource dataSource;

  @AfterEach
  void cleanup() throws Exception {
    // Draine la passe async du POST /sync avant de rendre la main : un download de fond encore
    // en cours écrirait dans le cache disque partagé qu'un autre test efface → course.
    for (int i = 0; i < 200 && syncService.isRunning(); i++) {
      Thread.sleep(25);
    }
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM dossier_signataires WHERE dossier_uid LIKE 'DLR5L17N9%'");
      st.executeUpdate("DELETE FROM dossier_candidates WHERE uid LIKE 'DLR5L17N9%'");
      st.executeUpdate("DELETE FROM laws WHERE id LIKE 'DLR5L17N9%'");
    }
  }

  /** Une requête telle que la page admin l'émet : origine jrec.fr + jeton. */
  private RequestSpecification browser() {
    return given().header("Origin", ORIGIN).header("X-Admin-Token", ADMIN);
  }

  private void seedCandidate() {
    candidates.upsert(
        CAND, 17, "Dossier de test", "https://www.assemblee-nationale.fr/dyn/17/dossiers/" + CAND,
        "eau", false, "Proposition de loi ordinaire", false, "En commission");
  }

  @Test
  void charger_candidates_depuis_origine_admin() {
    browser().when().get("/api/admin/dossiers/candidates").then().statusCode(200);
  }

  /**
   * Contrat CONSOMMÉ par la page admin (issue #33) : chaque candidat expose son initiateur
   * (nom + sigle + bloc) et ses cosignataires agrégés par groupe, triés par mobilisation. Le tri
   * global place le texte le plus cosigné en premier (signal d'importance).
   */
  @Test
  void candidates_enrichis_auteur_et_cosignataires_par_groupe() {
    seedCandidate(); // sans signataires — champs à zéro/null attendus
    candidates.upsert(
        "DLR5L17N99998", 17, "Texte très soutenu",
        "https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17N99998",
        "eau", false, "Proposition de loi ordinaire", false, "En 1re lecture à l'Assemblée");
    signataires.replaceForDossier(
        "DLR5L17N99998",
        List.of(
            new Signataire("auteur", "PA001", "Jeanne Martin", "EPR", "milieu"),
            new Signataire("cosignataire", "PA002", "Ali Dupont", "SOC", "gauche"),
            new Signataire("cosignataire", "PA003", "Lou Bernard", "SOC", "gauche"),
            new Signataire("cosignataire", "PA004", "Sam Petit", "RN", "extreme-droite"),
            new Signataire("cosignataire", "PA005", null, null, null))); // groupe non résolu → "?"
    String soutenu = "find { it.uid == 'DLR5L17N99998' }";
    List<String> uids =
        browser()
            .when()
            .get("/api/admin/dossiers/candidates")
            .then()
            .statusCode(200)
            .body(soutenu + ".auteur.nom", is("Jeanne Martin"))
            .body(soutenu + ".auteur.sigle", is("EPR"))
            .body(soutenu + ".auteur.bloc", is("milieu"))
            .body(soutenu + ".cosignatairesTotal", is(4))
            // Groupes triés par mobilisation décroissante ; groupe non résolu compté sous "?".
            .body(soutenu + ".cosignatairesParGroupe[0].sigle", is("SOC"))
            .body(soutenu + ".cosignatairesParGroupe[0].count", is(2))
            .body(soutenu + ".cosignatairesParGroupe[0].bloc", is("gauche"))
            .body(soutenu + ".cosignatairesParGroupe.sigle", hasItems("RN", "?"))
            // Candidat sans signataires : champs neutres, jamais d'erreur.
            .body("find { it.uid == '" + CAND + "' }.auteur", nullValue())
            .body("find { it.uid == '" + CAND + "' }.cosignatairesTotal", is(0))
            // L'étape officielle (sourcée) est exposée à la relecture.
            .body("find { it.uid == '" + CAND + "' }.stage", is("En commission"))
            .extract()
            .jsonPath()
            .getList("uid", String.class);
    // Tri par importance : à statut égal (hors gouvernement), le plus cosigné passe devant.
    org.junit.jupiter.api.Assertions.assertTrue(
        uids.indexOf("DLR5L17N99998") < uids.indexOf(CAND),
        "le candidat le plus cosigné doit précéder celui sans soutien : " + uids);
  }

  @Test
  void synchroniser_depuis_origine_admin_nest_pas_bloque_par_cors() {
    // LE bouton qui renvoyait 403 : un POST navigateur porte Origin: https://jrec.fr.
    browser().when().post("/api/admin/dossiers/sync").then().statusCode(anyOf(is(202), is(200)));
  }

  @Test
  void promouvoir_depuis_origine_admin() {
    seedCandidate();
    // La page admin n'envoie PLUS de date : la carte « à venir » affiche l'étape officielle.
    browser()
        .contentType("application/json")
        .body("{\"category\":\"eau\",\"summary\":\"résumé\",\"sourceExpect\":\"test\"}")
        .when()
        .post("/api/admin/dossiers/" + CAND + "/promote")
        .then()
        .statusCode(201);
  }

  @Test
  void rejeter_depuis_origine_admin() {
    seedCandidate();
    browser().when().post("/api/admin/dossiers/" + CAND + "/reject").then().statusCode(204);
  }

  @Test
  void sans_jeton_non_autorise() {
    given()
        .header("Origin", ORIGIN)
        .when()
        .get("/api/admin/dossiers/candidates")
        .then()
        .statusCode(401);
  }

  @Test
  void origine_inconnue_refusee_par_cors() {
    given()
        .header("Origin", "https://evil.example")
        .header("X-Admin-Token", ADMIN)
        .when()
        .post("/api/admin/dossiers/sync")
        .then()
        .statusCode(403);
  }
}

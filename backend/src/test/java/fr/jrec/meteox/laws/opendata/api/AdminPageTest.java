package fr.jrec.meteox.laws.opendata.api;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** La page d'admin des dossiers est servie en statique par le backend (même origine que l'API). */
@QuarkusTest
class AdminPageTest {

  @Test
  void admin_page_is_served() {
    // La base API est composée dans le JS : 'api/admin' + '/dossiers/…' ou '/corpus/…'.
    get("/admin.html")
        .then()
        .statusCode(200)
        .body(containsString("validation des dossiers"))
        .body(containsString("api/admin"))
        .body(containsString("/dossiers/"));
  }

  @Test
  void admin_page_exposes_procedure_ui_hooks() {
    // Filtre « Projets de loi uniquement » + badge gouvernement + affichage de la procédure.
    get("/admin.html")
        .then()
        .statusCode(200)
        .body(containsString("id=\"f-gov\""))
        .body(containsString("data-gov"))
        .body(containsString("data-procedure"));
  }

  @Test
  void admin_page_exposes_review_ux_hooks() {
    // UX de relecture (retours 2026-07-18) : dépublication (la carte publiée reste affichée,
    // badge « Publiée »), filtre « porté par » (bloc politique), tri par cosignataires, et lien
    // de reconnexion affiché quand la session expire (fini le « Jeton admin invalide » sec).
    get("/admin.html")
        .then()
        .statusCode(200)
        .body(containsString("data-act=\"demote\""))
        .body(containsString("data-pub"))
        .body(containsString("id=\"f-bloc\""))
        .body(containsString("id=\"f-sort\""))
        .body(containsString("id=\"reconnect\""))
        .body(containsString("Dépublier"));
  }

  @Test
  void admin_page_exposes_network_section() {
    // Analyse réseau (issue #33, prolongement) : matrice de soutien par bloc, liens entre
    // groupes, ponts transpartisans — branchée sur /api/admin/reseau.
    get("/admin.html")
        .then()
        .statusCode(200)
        .body(containsString("Réseaux de soutien"))
        .body(containsString("id=\"net-matrix\""))
        .body(containsString("id=\"net-links\""))
        .body(containsString("id=\"net-pairs\""))
        .body(containsString("/reseau"));
  }

  @Test
  void admin_page_exposes_corpus_section() {
    // Section « lois votées candidates » (issue #3, tâche 3) : liste, filtre, sync, promotion
    // avec dossier officiel requis (URL + fragment) — branchée sur /api/admin/corpus.
    get("/admin.html")
        .then()
        .statusCode(200)
        .body(containsString("lois votées candidates"))
        .body(containsString("id=\"law-tpl\""))
        .body(containsString("id=\"law-sync\""))
        .body(containsString("id=\"lf-theme\""))
        .body(containsString("data-f=\"textUrl\""))
        .body(containsString("/corpus/"));
  }
}

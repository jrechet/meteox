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

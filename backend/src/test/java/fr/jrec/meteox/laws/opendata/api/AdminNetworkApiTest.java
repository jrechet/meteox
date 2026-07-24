package fr.jrec.meteox.laws.opendata.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import fr.jrec.meteox.laws.opendata.DossierSignataireRepository;
import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.Signataire;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Endpoint d'analyse réseau, testé COMME la page admin l'appelle (en-tête {@code Origin}) —
 * DoD : 100 % des actions via le vrai chemin. Auth requise, origine inconnue refusée (CORS).
 */
@QuarkusTest
class AdminNetworkApiTest {

  private static final String ADMIN = "test-admin-token";
  private static final String ORIGIN = "https://jrec.fr";
  private static final String D1 = "DLR5L17N96001";
  private static final String D2 = "DLR5L17N96002";

  @Inject DossierSignataireRepository signataires;
  @Inject DataSource dataSource;

  @AfterEach
  void cleanup() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM dossier_signataires WHERE dossier_uid LIKE 'DLR5L17N96%'");
    }
  }

  @Test
  void reseau_depuis_origine_admin_rend_les_quatre_lectures() {
    for (String d : List.of(D1, D2)) {
      signataires.replaceForDossier(
          d,
          List.of(
              new Signataire("auteur", "PA800", "Aya Blanc", "LFI-NFP", "gauche"),
              new Signataire("cosignataire", "PA801", "Max Noir", "HOR", "milieu")));
    }
    given()
        .header("Origin", ORIGIN)
        .header("X-Admin-Token", ADMIN)
        .when()
        .get("/api/admin/reseau")
        .then()
        .statusCode(200)
        .body("groupes.size()", greaterThanOrEqualTo(2))
        .body("groupes.find { it.sigle == 'LFI-NFP' }.bloc", is("gauche"))
        .body("liens.find { it.a == 'HOR' && it.b == 'LFI-NFP' }.dossiers", is(2))
        .body("soutienParBloc.find { it.auteurBloc == 'gauche' && it.cosignataireBloc == 'milieu' }.cosignatures", is(2))
        .body(
            "pontsTranspartisans.find { it.cosignataireNom == 'Max Noir' }.dossiers", is(2));
  }

  @Test
  void reseau_sans_jeton_non_autorise() {
    given().header("Origin", ORIGIN).when().get("/api/admin/reseau").then().statusCode(401);
  }

  @Test
  void reseau_origine_inconnue_refusee_par_cors() {
    given()
        .header("Origin", "https://evil.example")
        .header("X-Admin-Token", ADMIN)
        .when()
        .get("/api/admin/reseau")
        .then()
        .statusCode(403);
  }
}

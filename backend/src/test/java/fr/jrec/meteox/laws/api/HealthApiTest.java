package fr.jrec.meteox.laws.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthApiTest {

  @Test
  void health_returns_200_with_database_up() {
    given()
        .when()
        .get("/api/health")
        .then()
        .statusCode(200)
        .body("status", is("UP"))
        .body("database", is("UP"));
  }
}

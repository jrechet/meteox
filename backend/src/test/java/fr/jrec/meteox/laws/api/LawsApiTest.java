package fr.jrec.meteox.laws.api;

import static io.restassured.RestAssured.get;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Acceptance issues #2/#5 : GET /api/laws renvoie exactement les lois du snapshot committé
 * (src/data/laws-snapshot.json). Depuis #5 le front n'embarque plus LAWS_DATA en dur — sa
 * source de repli est ce snapshot, régénéré depuis CETTE MÊME API au build. L'invariant testé
 * ici garantit que l'API et le snapshot embarqué ne divergent jamais (sinon build front à
 * régénérer). Toute évolution du seed doit donc s'accompagner d'un snapshot régénéré.
 */
@QuarkusTest
class LawsApiTest {

  /** Snapshot de repli du front, relatif au module backend/. */
  private static final Path SNAPSHOT = Path.of("..", "src", "data", "laws-snapshot.json");

  @Test
  void api_laws_matches_committed_snapshot() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode expected = mapper.readTree(Files.readString(SNAPSHOT)).path("laws");
    assertEquals(4, expected.size(), "le snapshot doit contenir les 4 lois vérifiées");

    JsonNode actual =
        mapper.readTree(get("/api/laws").then().statusCode(200).extract().asString());

    assertEquals(
        normalize(expected),
        normalize(actual),
        "GET /api/laws doit être identique au snapshot committé src/data/laws-snapshot.json"
            + " (régénère-le via `npm run generate:snapshot` si le seed a changé)");
  }

  /** Canonise les nombres (1 == 1.0) pour une comparaison structurelle stricte du reste. */
  private static JsonNode normalize(JsonNode node) {
    ObjectMapper m = new ObjectMapper();
    if (node.isObject()) {
      ObjectNode out = m.createObjectNode();
      node.fields().forEachRemaining(e -> out.set(e.getKey(), normalize(e.getValue())));
      return out;
    }
    if (node.isArray()) {
      ArrayNode out = m.createArrayNode();
      node.forEach(child -> out.add(normalize(child)));
      return out;
    }
    if (node.isNumber()) {
      return m.getNodeFactory()
          .textNode(new BigDecimal(node.asText()).stripTrailingZeros().toPlainString());
    }
    return node;
  }
}

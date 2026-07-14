package fr.jrec.meteox.laws.api;

import static io.restassured.RestAssured.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Acceptance issue #2 : GET /api/laws renvoie exactement les 4 lois vérifiées, avec des
 * données STRICTEMENT identiques à src/lib/laws.js (diff automatisé contre le fichier source).
 */
@QuarkusTest
class LawsApiTest {

  /** Chemin de laws.js relatif au module backend/. */
  private static final Path LAWS_JS = Path.of("..", "src", "lib", "laws.js");

  @Test
  void api_laws_matches_laws_js_exactly() throws Exception {
    JsonNode expected = parseLawsJs();
    assertEquals(4, expected.size(), "laws.js doit contenir les 4 lois vérifiées");

    JsonNode actual = new ObjectMapper().readTree(get("/api/laws").then().statusCode(200)
        .extract().asString());

    assertEquals(normalize(expected), normalize(actual),
        "GET /api/laws doit être identique à LAWS_DATA de src/lib/laws.js");
  }

  /** Extrait et parse le littéral LAWS_DATA de laws.js (commentaires, quotes simples, clés nues). */
  private static JsonNode parseLawsJs() throws Exception {
    String js = Files.readString(LAWS_JS);
    int marker = js.indexOf("LAWS_DATA = [");
    assertTrue(marker >= 0, "export const LAWS_DATA introuvable dans " + LAWS_JS);
    String array = js.substring(js.indexOf('[', marker), js.lastIndexOf(']') + 1);
    ObjectMapper lenient =
        JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .build();
    return lenient.readTree(array);
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

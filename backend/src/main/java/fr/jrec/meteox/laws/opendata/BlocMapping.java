package fr.jrec.meteox.laws.opendata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mapping organeRef (code PO d'un groupe politique AN) → bloc, chargé depuis la référence
 * committée {@code reference/organe-blocs.json} (issue #3). Sources : sigles officiels de
 * l'open data AN (jeu AMO10 organes, codeType=GP). Le bloc {@code horsBlocs} marque les
 * non-inscrits, exclus des agrégats mais connus — pour distinguer un « à ignorer » d'un
 * organeRef inconnu (Golden Rule : un nouveau groupe ne doit jamais être silencieusement perdu).
 */
@ApplicationScoped
public class BlocMapping {

  public static final String HORS_BLOCS = "horsBlocs";

  private final Map<String, String> blocByOrgane = new HashMap<>();
  private final Map<String, String> sigleByOrgane = new HashMap<>();

  @PostConstruct
  void load() {
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("reference/organe-blocs.json")) {
      if (in == null) {
        throw new IllegalStateException("reference/organe-blocs.json introuvable dans le classpath");
      }
      JsonNode root = new ObjectMapper().readTree(in);
      for (JsonNode o : root.path("organes")) {
        String ref = o.path("organeRef").asText();
        blocByOrgane.put(ref, o.path("bloc").asText());
        sigleByOrgane.put(ref, o.path("sigle").asText());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Chargement de organe-blocs.json impossible", e);
    }
  }

  /** Bloc d'un organeRef connu (vide si l'organeRef n'est pas référencé). */
  public Optional<String> blocFor(String organeRef) {
    return Optional.ofNullable(blocByOrgane.get(organeRef));
  }

  public boolean isKnown(String organeRef) {
    return blocByOrgane.containsKey(organeRef);
  }

  /** Vrai pour les non-inscrits : connus mais exclus des agrégats par bloc. */
  public boolean isHorsBlocs(String organeRef) {
    return HORS_BLOCS.equals(blocByOrgane.get(organeRef));
  }

  public Optional<String> sigleFor(String organeRef) {
    return Optional.ofNullable(sigleByOrgane.get(organeRef));
  }
}

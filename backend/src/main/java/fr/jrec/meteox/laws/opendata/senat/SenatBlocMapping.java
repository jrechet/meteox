package fr.jrec.meteox.laws.opendata.senat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mapping code groupe ODSEN (groupe politique du Sénat) → bloc, chargé depuis la référence
 * committée {@code reference/senat-groupe-blocs.json} (issue #3, tâche 4). Pendant Sénat de
 * {@link fr.jrec.meteox.laws.opendata.BlocMapping} (AN) : les codes ODSEN sont historiques
 * (UMP = Les Républicains, LREM = RDPI, CRC = CRCE-Kanaky…), documentés dans le fichier de
 * référence. Le bloc {@code horsBlocs} marque les groupes exclus des agrégats mais connus (RDSE) —
 * pour distinguer un « à ignorer » d'un code inconnu (Golden Rule : un nouveau groupe ne doit
 * jamais être silencieusement perdu ni fausser un total).
 */
@ApplicationScoped
public class SenatBlocMapping {

  public static final String HORS_BLOCS = "horsBlocs";

  private final Map<String, String> blocByCode = new HashMap<>();
  private final Map<String, String> libelleByCode = new HashMap<>();

  @PostConstruct
  void load() {
    try (InputStream in =
        Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream("reference/senat-groupe-blocs.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "reference/senat-groupe-blocs.json introuvable dans le classpath");
      }
      JsonNode root = new ObjectMapper().readTree(in);
      for (JsonNode g : root.path("groupes")) {
        String code = g.path("code").asText();
        blocByCode.put(code, g.path("bloc").asText());
        libelleByCode.put(code, g.path("libelle").asText());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Chargement de senat-groupe-blocs.json impossible", e);
    }
  }

  /** Bloc d'un code groupe connu (vide si le code n'est pas référencé). */
  public Optional<String> blocFor(String code) {
    return Optional.ofNullable(blocByCode.get(code));
  }

  public boolean isKnown(String code) {
    return blocByCode.containsKey(code);
  }

  /** Vrai pour les groupes transversaux exclus des agrégats par bloc (RDSE). */
  public boolean isHorsBlocs(String code) {
    return HORS_BLOCS.equals(blocByCode.get(code));
  }

  public Optional<String> libelleFor(String code) {
    return Optional.ofNullable(libelleByCode.get(code));
  }
}

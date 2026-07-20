package fr.jrec.meteox.laws.opendata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Parse un dossier législatif de l'open data AN (jeu {@code dossiers_legislatifs},
 * nœud {@code dossierParlementaire}) en une représentation neutre. On ne lit que ce qui
 * sert à la détection : identité, titre, et présence d'un acte de promulgation (=> loi
 * promulguée, dossier clos, jamais « à venir »).
 */
@ApplicationScoped
public class DossierParser {

  private final ObjectMapper mapper = new ObjectMapper();

  private static final String CODE_DEPOT = "AN1-DEPOT";

  // Étape courante du dossier (SOURCÉE : l'acte le plus avancé de l'arbre `actesLegislatifs`).
  // Golden Rule : jamais de date fabriquée pour une carte « à venir » — on affiche l'étape
  // officielle. Vocabulaire conservateur ; un acte non reconnu retombe sur le repli neutre.
  /** Repli neutre quand aucune étape n'est datée/reconnue (jamais d'invention). */
  public static final String STAGE_FALLBACK = "En cours d'examen";

  private static final String STAGE_DEPOT = "Déposé";
  private static final String STAGE_COMMISSION = "En commission";
  private static final String STAGE_CMP = "En commission mixte paritaire";
  private static final String STAGE_PROMULGUEE = "Promulguée";

  /** Étape la plus avancée d'abord : date décroissante, puis profondeur, puis ordre de parcours. */
  private static final Comparator<DatedActe> BY_ADVANCEMENT =
      Comparator.comparing(DatedActe::date)
          .thenComparingInt(DatedActe::depth)
          .thenComparingInt(DatedActe::order);

  /** Dossier parsé (données brutes, avant filtrage thématique). */
  public record ParsedDossier(
      String uid,
      int legislature,
      String titre,
      String titreChemin,
      String procedure,
      boolean promulgated,
      String depotDocumentRef,
      String stage) {

    /** URL officielle publique et stable du dossier (l'uid résout ; le slug non). */
    public String url() {
      return "https://www.assemblee-nationale.fr/dyn/" + legislature + "/dossiers/" + uid;
    }
  }

  /** Un acte daté de l'arbre, avec sa profondeur et le libellé de sa phase racine (lecture). */
  private record DatedActe(String codeActe, LocalDate date, int depth, int order, String rootLibelle) {}

  // Rangs de progression d'une lecture : dépôt &lt; commission &lt; séance. Les actes ANNEXES
  // (étude d'impact, avis du Conseil d'État, procédure accélérée…) ne font pas avancer la navette
  // et gardent le rang « autre » : ils ne doivent pas primer sur un vrai dépôt le même jour.
  private static final int RANK_OTHER = 0;
  private static final int RANK_DEPOT = 1;
  private static final int RANK_COMMISSION = 2;
  private static final int RANK_SEANCE = 3;

  public ParsedDossier parse(InputStream json) {
    try {
      JsonNode d = mapper.readTree(json).path("dossierParlementaire");
      if (d.isMissingNode()) {
        throw new IllegalArgumentException("JSON de dossier invalide : nœud 'dossierParlementaire' absent");
      }
      String uid = d.path("uid").asText();
      int legislature = d.path("legislature").asInt();
      JsonNode titreNode = d.path("titreDossier");
      String titre = titreNode.path("titre").asText();
      String chemin = titreNode.path("titreChemin").asText(null);
      String procedure = d.path("procedureParlementaire").path("libelle").asText("");
      if (uid.isBlank() || titre.isBlank()) {
        throw new IllegalArgumentException("Dossier sans uid ou titre exploitable");
      }
      JsonNode actes = d.path("actesLegislatifs");
      return new ParsedDossier(
          uid,
          legislature,
          titre,
          chemin,
          procedure,
          hasPromulgation(actes),
          findDepotDocumentRef(actes).orElse(null),
          extractStage(actes));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Parsing du dossier impossible : " + e.getMessage(), e);
    }
  }

  /** Vrai si l'arbre des actes contient un acte de promulgation ({@code codeActe} en PROM…). */
  private static boolean hasPromulgation(JsonNode actes) {
    if (actes.isObject()) {
      JsonNode code = actes.get("codeActe");
      if (code != null && code.asText("").startsWith("PROM")) {
        return true;
      }
      for (JsonNode child : actes) {
        if (hasPromulgation(child)) {
          return true;
        }
      }
    } else if (actes.isArray()) {
      for (JsonNode child : actes) {
        if (hasPromulgation(child)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Uid du document de dépôt : parcours en profondeur de l'arbre des actes, on renvoie le
   * {@code texteAssocie} du premier acte {@code codeActe == "AN1-DEPOT"} (1er dépôt à l'Assemblée).
   * Vide si le dossier n'en porte pas — c'est un cas normal (dossier sans texte déposé côté AN).
   */
  private static Optional<String> findDepotDocumentRef(JsonNode actes) {
    if (actes.isObject()) {
      JsonNode code = actes.get("codeActe");
      if (code != null && CODE_DEPOT.equals(code.asText(null))) {
        String texte = actes.path("texteAssocie").asText(null);
        if (texte != null && !texte.isBlank()) {
          return Optional.of(texte);
        }
      }
      for (JsonNode child : actes) {
        Optional<String> found = findDepotDocumentRef(child);
        if (found.isPresent()) {
          return found;
        }
      }
    } else if (actes.isArray()) {
      for (JsonNode child : actes) {
        Optional<String> found = findDepotDocumentRef(child);
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Étape courante du dossier = l'acte le plus avancé de l'arbre {@code actesLegislatifs},
   * c.-à-d. l'acte DATÉ le plus récent (à égalité de date, le plus profond de la navette, puis le
   * dernier rencontré). Le libellé rendu est conservateur et TOUJOURS sourcé (jamais d'invention) :
   * un {@code codeActe} inconnu, ou un dossier sans acte daté, retombe sur {@link #STAGE_FALLBACK}.
   */
  static String extractStage(JsonNode actesRoot) {
    List<DatedActe> dated = new ArrayList<>();
    collectDatedActes(actesRoot, "", 0, new int[] {0}, dated);
    return dated.stream()
        .max(BY_ADVANCEMENT)
        .map(latest -> labelFor(latest, dated))
        .orElse(STAGE_FALLBACK);
  }

  /**
   * Parcours en profondeur de l'arbre des actes. Chaque acte DATÉ est collecté avec sa profondeur
   * (les sous-étapes d'une commission sont « plus profondes » qu'un dépôt) et le libellé de sa phase
   * racine (« 1ère lecture (2ème assemblée saisie) » — sert à distinguer une navette).
   */
  private static void collectDatedActes(
      JsonNode node, String rootLibelle, int depth, int[] order, List<DatedActe> out) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        collectDatedActes(child, rootLibelle, depth, order, out);
      }
      return;
    }
    if (!node.isObject()) {
      return;
    }
    JsonNode codeNode = node.get("codeActe");
    if (codeNode == null) {
      // Nœud conteneur { "acteLegislatif": <acte ou tableau d'actes> }.
      collectDatedActes(node.get("acteLegislatif"), rootLibelle, depth, order, out);
      return;
    }
    // Nœud acte : la phase racine (profondeur 0) porte le libellé de lecture, hérité par ses enfants.
    String code = codeNode.asText("");
    String libelle =
        depth == 0 ? node.path("libelleActe").path("nomCanonique").asText("") : rootLibelle;
    LocalDate date = parseActeDate(node.path("dateActe").asText(null));
    if (!code.isBlank() && date != null) {
      out.add(new DatedActe(code, date, depth, order[0]++, libelle));
    }
    collectDatedActes(node.get("actesLegislatifs"), libelle, depth + 1, order, out);
  }

  /** {@code dateActe} en {@code YYYY-MM-DD} (le suffixe horaire éventuel est ignoré) ; null si absente. */
  private static LocalDate parseActeDate(String raw) {
    if (raw == null || raw.length() < 10) {
      return null;
    }
    try {
      return LocalDate.parse(raw.substring(0, 10));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /**
   * Traduit la phase courante (celle de l'acte daté le plus avancé) en libellé français
   * conservateur. La sous-étape retenue est la PLUS AVANCÉE réellement atteinte dans cette phase
   * (dépôt &lt; commission &lt; séance), en ignorant les actes annexes — sinon une étude d'impact
   * déposée le même jour que le texte ferait passer un dossier « à peine déposé » pour « en séance ».
   */
  private static String labelFor(DatedActe latest, List<DatedActe> all) {
    String phase = phaseOf(latest.codeActe());
    // La CMP et la promulgation priment : elles décrivent le dossier quelle que soit la sous-étape.
    if ("CMP".equals(phase)) {
      return STAGE_CMP;
    }
    if (phase.startsWith("PROM")) {
      return STAGE_PROMULGUEE; // défensif : un dossier promulgué n'est jamais une carte « à venir ».
    }
    int reached =
        all.stream()
            .filter(a -> phase.equals(phaseOf(a.codeActe())))
            .mapToInt(a -> stepRank(a.codeActe()))
            .max()
            .orElse(RANK_OTHER);
    if (reached == RANK_DEPOT) {
      return STAGE_DEPOT;
    }
    if (reached == RANK_COMMISSION) {
      return STAGE_COMMISSION;
    }
    // Séance (ou aucune sous-étape standard) : on nomme la chambre et la lecture.
    String chambre = chamberLecture(phase, isNavette(latest.rootLibelle()));
    return chambre != null ? chambre : STAGE_FALLBACK;
  }

  /** Préfixe de phase du {@code codeActe} : le segment avant le 1er tiret (AN1, SN1, CMP, PROM…). */
  private static String phaseOf(String codeActe) {
    int dash = codeActe.indexOf('-');
    return dash < 0 ? codeActe : codeActe.substring(0, dash);
  }

  /** Rang de progression d'un acte dans sa lecture : séance &gt; commission &gt; dépôt &gt; annexe. */
  private static int stepRank(String codeActe) {
    Set<String> tokens = Set.of(codeActe.split("-"));
    if (tokens.contains("DEBATS") || tokens.contains("SEANCE") || tokens.contains("DEC")) {
      return RANK_SEANCE;
    }
    if (tokens.contains("COM")) {
      return RANK_COMMISSION;
    }
    if (tokens.contains("DEPOT")) {
      return RANK_DEPOT;
    }
    return RANK_OTHER;
  }

  /** Vrai si la phase racine est saisie en 2ème (navette) — lu sur le libellé officiel de la lecture. */
  private static boolean isNavette(String rootLibelle) {
    if (rootLibelle == null) {
      return false;
    }
    String s = rootLibelle.toLowerCase(Locale.ROOT);
    return s.contains("saisie")
        && (s.contains("2ème") || s.contains("2eme") || s.contains("2e ") || s.contains("(2"));
  }

  /** Libellé « chambre + lecture » d'une phase ; null si la phase n'est pas reconnue (→ repli). */
  private static String chamberLecture(String phase, boolean navette) {
    return switch (phase) {
      case "AN1" -> navette ? "En navette à l'Assemblée" : "En 1re lecture à l'Assemblée";
      case "SN1" -> navette ? "En navette au Sénat" : "En 1re lecture au Sénat";
      case "AN2" -> "En 2e lecture à l'Assemblée";
      case "SN2" -> "En 2e lecture au Sénat";
      case "ANLUNI" -> "En lecture unique à l'Assemblée";
      case "SNLUNI" -> "En lecture unique au Sénat";
      case "ANNLEC" -> "En nouvelle lecture à l'Assemblée";
      case "SNNLEC" -> "En nouvelle lecture au Sénat";
      case "ANLDEF", "ANLECDEF" -> "En lecture définitive à l'Assemblée";
      case "SNLDEF" -> "En lecture définitive au Sénat";
      default -> null;
    };
  }
}

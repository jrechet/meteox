package fr.jrec.meteox.laws.opendata.acteurs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse un acteur de l'open data AN (jeu AMO30, nœud racine {@code acteur}) en une
 * représentation neutre : identité et rattachement au groupe politique ACTIF. Le mandat de
 * groupe (typeOrgane {@code GP}) actif est celui dont {@code dateFin} est nul ; il pointe le
 * groupe via {@code organes.organeRef}. Un acteur sans mandat GP actif (non-inscrit, ancien
 * député) a un {@code groupeOrganeRef} nul — c'est un cas normal, pas une erreur.
 */
@ApplicationScoped
public class ActeurParser {

  private final ObjectMapper mapper = new ObjectMapper();

  /** Acteur parsé (données brutes, avant résolution du sigle/bloc). */
  public record ParsedActeur(String uid, String prenom, String nom, String groupeOrganeRef) {}

  public ParsedActeur parse(InputStream json) {
    try {
      JsonNode a = mapper.readTree(json).path("acteur");
      if (a.isMissingNode()) {
        throw new IllegalArgumentException("JSON d'acteur invalide : nœud 'acteur' absent");
      }
      String uid = readUid(a.path("uid"));
      JsonNode ident = a.path("etatCivil").path("ident");
      String prenom = ident.path("prenom").asText("");
      String nom = ident.path("nom").asText("");
      if (uid.isBlank()) {
        throw new IllegalArgumentException("Acteur sans uid exploitable");
      }
      return new ParsedActeur(
          uid, prenom, nom, activeGroupeOrganeRef(a.path("mandats").path("mandat")));
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Parsing de l'acteur impossible : " + e.getMessage(), e);
    }
  }

  /** L'uid AN est un objet typé ({@code {"#text": "PA…"}}) ; certains flux le donnent en texte brut. */
  private static String readUid(JsonNode uid) {
    return uid.has("#text") ? uid.path("#text").asText("") : uid.asText("");
  }

  /**
   * organeRef du mandat GP actif (dateFin absente ou nulle), ou {@code null} si aucun. Le nœud
   * {@code mandat} est un OBJET quand il n'y en a qu'un, une LISTE sinon — les deux sont gérés.
   */
  private static String activeGroupeOrganeRef(JsonNode mandat) {
    if (mandat.isMissingNode()) {
      return null;
    }
    // Un OBJET (mandat unique) est traité comme une liste d'un élément ; une LISTE est parcourue telle quelle.
    List<JsonNode> mandats = new ArrayList<>();
    if (mandat.isArray()) {
      mandat.forEach(mandats::add);
    } else {
      mandats.add(mandat);
    }
    for (JsonNode m : mandats) {
      if (!"GP".equals(m.path("typeOrgane").asText())) {
        continue;
      }
      JsonNode dateFin = m.path("dateFin");
      if (dateFin.isNull() || dateFin.isMissingNode()) {
        String ref = m.path("organes").path("organeRef").asText(null);
        if (ref != null && !ref.isBlank()) {
          return ref;
        }
      }
    }
    return null;
  }
}

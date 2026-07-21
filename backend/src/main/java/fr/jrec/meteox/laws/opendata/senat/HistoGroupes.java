package fr.jrec.meteox.laws.opendata.senat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Index de l'historique d'appartenance des sénateurs aux groupes politiques (jeu open data
 * {@code ODSEN_HISTOGROUPES}). Chaque matricule porte une ou plusieurs périodes d'appartenance
 * datées ; {@link #groupeAt(String, LocalDate)} rend le code du groupe (ODSEN) à une date donnée —
 * indispensable pour agréger un scrutin par groupe À LA DATE du vote (un sénateur peut changer de
 * groupe au fil du temps).
 */
public final class HistoGroupes {

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  private record Period(String code, LocalDate debut, LocalDate fin) {}

  private final Map<String, List<Period>> byMatricule;

  private HistoGroupes(Map<String, List<Period>> byMatricule) {
    this.byMatricule = byMatricule;
  }

  public int size() {
    return byMatricule.size();
  }

  /** Code du groupe (ODSEN) auquel le matricule appartient à la date donnée, s'il y en a un. */
  public Optional<String> groupeAt(String matricule, LocalDate when) {
    for (Period p : byMatricule.getOrDefault(matricule, List.of())) {
      boolean afterStart = p.debut() == null || !when.isBefore(p.debut());
      boolean beforeEnd = p.fin() == null || !when.isAfter(p.fin());
      if (afterStart && beforeEnd) {
        return Optional.of(p.code());
      }
    }
    return Optional.empty();
  }

  /** Construit l'index depuis la racine JSON d'{@code ODSEN_HISTOGROUPES} (nœud {@code results}). */
  public static HistoGroupes parse(JsonNode root) {
    Map<String, List<Period>> byMatricule = new HashMap<>();
    for (JsonNode r : root.path("results")) {
      String matricule = r.path("Matricule").asText(null);
      String code = r.path("Code_du_groupe_politique").asText(null);
      if (matricule == null || code == null || code.isBlank()) {
        continue;
      }
      byMatricule
          .computeIfAbsent(matricule.trim(), k -> new ArrayList<>())
          .add(
              new Period(
                  code.trim(),
                  parseDate(r.path("Date_de_debut_d_appartenance").asText(null)),
                  parseDate(r.path("Date_de_fin_d_appartenance").asText(null))));
    }
    return new HistoGroupes(byMatricule);
  }

  private static LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank() || "null".equals(raw)) {
      return null;
    }
    String d = raw.trim();
    if (d.length() >= 10) {
      d = d.substring(0, 10);
    }
    try {
      return LocalDate.parse(d, FMT);
    } catch (Exception e) {
      return null; // date illisible = période ouverte de ce côté (best-effort)
    }
  }
}

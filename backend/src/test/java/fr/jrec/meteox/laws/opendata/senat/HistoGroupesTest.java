package fr.jrec.meteox.laws.opendata.senat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Jointure matricule → groupe À LA DATE du scrutin ({@code ODSEN_HISTOGROUPES}) : un sénateur peut
 * changer de groupe, seule la période couvrant la date compte. Bornes nulles = période ouverte.
 */
class HistoGroupesTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HistoGroupes parse(String json) throws Exception {
    return HistoGroupes.parse(MAPPER.readTree(json));
  }

  @Test
  void picks_the_membership_period_covering_the_scrutin_date() throws Exception {
    // M1 : SOC jusqu'au 2022-01-01, puis UMP à partir du 2022-01-02.
    HistoGroupes h =
        parse(
            "{\"results\":["
                + "{\"Matricule\":\"M1\",\"Code_du_groupe_politique\":\"SOC\","
                + "\"Date_de_debut_d_appartenance\":null,\"Date_de_fin_d_appartenance\":\"2022/01/01 00:00:00\"},"
                + "{\"Matricule\":\"M1\",\"Code_du_groupe_politique\":\"UMP\","
                + "\"Date_de_debut_d_appartenance\":\"2022/01/02 00:00:00\",\"Date_de_fin_d_appartenance\":null}"
                + "]}");

    assertEquals("UMP", h.groupeAt("M1", LocalDate.of(2023, 2, 7)).orElseThrow());
    assertEquals("SOC", h.groupeAt("M1", LocalDate.of(2021, 6, 1)).orElseThrow());
    // Bornes incluses.
    assertEquals("SOC", h.groupeAt("M1", LocalDate.of(2022, 1, 1)).orElseThrow());
    assertEquals("UMP", h.groupeAt("M1", LocalDate.of(2022, 1, 2)).orElseThrow());
  }

  @Test
  void empty_when_matricule_unknown_or_no_period_covers_the_date() throws Exception {
    HistoGroupes h =
        parse(
            "{\"results\":[{\"Matricule\":\"M1\",\"Code_du_groupe_politique\":\"SOC\","
                + "\"Date_de_debut_d_appartenance\":\"2020/01/01 00:00:00\","
                + "\"Date_de_fin_d_appartenance\":\"2021/01/01 00:00:00\"}]}");
    assertTrue(h.groupeAt("GHOST", LocalDate.of(2023, 2, 7)).isEmpty());
    assertTrue(h.groupeAt("M1", LocalDate.of(2023, 2, 7)).isEmpty(), "hors de toute période");
  }
}

package fr.jrec.meteox.laws.opendata.senat;

import fr.jrec.meteox.laws.opendata.senat.DoslegDataset.Lecass;
import fr.jrec.meteox.laws.opendata.senat.DoslegDataset.Lecture;
import fr.jrec.meteox.laws.opendata.senat.DoslegDataset.Loi;
import fr.jrec.meteox.laws.opendata.senat.DoslegDataset.Scrutin;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse le dump Dosleg (Sénat) — un export PostgreSQL en texte tabulé — en {@link DoslegDataset}.
 * En <b>streaming</b> ligne à ligne : seules les sections {@code COPY} utiles (loi, lecture,
 * lecass, date_seance, scr) sont matérialisées ; les autres (dont {@code votsen}, ~75 % du
 * fichier) sont sautées sans allocation. Format {@code COPY} : colonnes séparées par des
 * tabulations, {@code \N} pour NULL, échappements PostgreSQL ({@code \t \n \r \\}), colonnes CHAR
 * complétées par des espaces (valeurs {@code trim}ées).
 *
 * <p>Les colonnes sont repérées par NOM depuis l'en-tête {@code COPY table (col, …)} : si une
 * colonne attendue disparaît d'un export futur, le parsing échoue bruyamment (le format Dosleg
 * n'est pas contractuel — cf. note § 6) plutôt que de produire des totaux faux.
 */
@ApplicationScoped
public class DoslegParser {

  private static final Pattern COPY_HEADER =
      Pattern.compile("^COPY (?:\\w+\\.)?(\\w+) \\(([^)]*)\\) FROM stdin;");
  private static final String ROW_TERMINATOR = "\\.";
  private static final Set<String> WANTED =
      Set.of("loi", "lecture", "lecass", "date_seance", "scr");

  public DoslegDataset parse(InputStream sql) throws IOException {
    Map<String, Loi> loiByRef = new HashMap<>();
    Map<String, List<Lecture>> lecturesByLoicod = new HashMap<>();
    Map<String, List<Lecass>> lecassByLecidt = new HashMap<>();
    Map<String, List<String>> codesByLecassidt = new HashMap<>();
    Map<String, List<Scrutin>> scrutinsByCode = new HashMap<>();

    BufferedReader reader =
        new BufferedReader(new InputStreamReader(sql, StandardCharsets.UTF_8), 1 << 16);
    String line;
    String table = null; // table courante si dans un bloc COPY voulu
    int[] cols = null; // indices des colonnes utiles pour cette table
    boolean skipping = false; // dans un bloc COPY non voulu (sauté)

    while ((line = reader.readLine()) != null) {
      if (table == null && !skipping) {
        Matcher m = COPY_HEADER.matcher(line);
        if (m.find()) {
          String t = m.group(1);
          if (WANTED.contains(t)) {
            table = t;
            cols = columnIndices(t, m.group(2));
          } else {
            skipping = true;
          }
        }
        continue;
      }
      if (line.equals(ROW_TERMINATOR)) {
        table = null;
        cols = null;
        skipping = false;
        continue;
      }
      if (skipping) {
        continue;
      }
      String[] f = line.split("\t", -1);
      switch (table) {
        case "loi" -> readLoi(f, cols, loiByRef);
        case "lecture" -> readLecture(f, cols, lecturesByLoicod);
        case "lecass" -> readLecass(f, cols, lecassByLecidt);
        case "date_seance" -> readSeance(f, cols, codesByLecassidt);
        case "scr" -> readScr(f, cols, scrutinsByCode);
        default -> { /* inatteignable */ }
      }
    }
    return new DoslegDataset(
        loiByRef, lecturesByLoicod, lecassByLecidt, codesByLecassidt, scrutinsByCode);
  }

  private static void readLoi(String[] f, int[] cols, Map<String, Loi> out) {
    String urlAn = field(f, cols[2]);
    if (urlAn == null || urlAn.isBlank()) {
      return; // 3 074 / 12 393 dossiers portent un lien AN — seuls ceux-là sont indexables
    }
    String loicod = field(f, cols[0]);
    if (loicod == null || loicod.isBlank()) {
      return;
    }
    out.putIfAbsent(
        DoslegDataset.normalizeAnRef(urlAn), new Loi(loicod, field(f, cols[1]), urlAn));
  }

  private static void readLecture(String[] f, int[] cols, Map<String, List<Lecture>> out) {
    String lecidt = field(f, cols[0]);
    String loicod = field(f, cols[1]);
    if (lecidt == null || loicod == null) {
      return;
    }
    out.computeIfAbsent(loicod, k -> new ArrayList<>())
        .add(new Lecture(lecidt, field(f, cols[2]), orEmpty(field(f, cols[3]))));
  }

  private static void readLecass(String[] f, int[] cols, Map<String, List<Lecass>> out) {
    String lecassidt = field(f, cols[0]);
    String lecidt = field(f, cols[1]);
    if (lecassidt == null || lecidt == null) {
      return;
    }
    out.computeIfAbsent(lecidt, k -> new ArrayList<>()).add(new Lecass(lecassidt, lecidt));
  }

  private static void readSeance(String[] f, int[] cols, Map<String, List<String>> out) {
    // Quirk Dosleg : date_seance.lecidt porte en réalité un lecassidt (cf. note § 3.2).
    String lecassidt = field(f, cols[0]);
    String code = field(f, cols[1]);
    if (lecassidt == null || code == null) {
      return;
    }
    out.computeIfAbsent(lecassidt, k -> new ArrayList<>()).add(code);
  }

  private static void readScr(String[] f, int[] cols, Map<String, List<Scrutin>> out) {
    Integer session = asInt(field(f, cols[0]));
    Integer numero = asInt(field(f, cols[1]));
    String code = field(f, cols[2]);
    if (session == null || numero == null || code == null) {
      return;
    }
    out.computeIfAbsent(code, k -> new ArrayList<>())
        .add(new Scrutin(session, numero, orEmpty(field(f, cols[3])), datePart(field(f, cols[4]))));
  }

  /** Indices, dans l'en-tête COPY, des colonnes utiles à la table — échoue si l'une manque. */
  private static int[] columnIndices(String table, String header) {
    List<String> names = new ArrayList<>();
    for (String n : header.split(",")) {
      names.add(n.trim());
    }
    String[] needed =
        switch (table) {
          case "loi" -> new String[] {"loicod", "signet", "url_an"};
          case "lecture" -> new String[] {"lecidt", "loicod", "typleccod", "leccom"};
          case "lecass" -> new String[] {"lecassidt", "lecidt"};
          case "date_seance" -> new String[] {"lecidt", "code"};
          case "scr" -> new String[] {"sesann", "scrnum", "code", "scrint", "scrdat"};
          default -> new String[0];
        };
    int[] idx = new int[needed.length];
    for (int i = 0; i < needed.length; i++) {
      idx[i] = names.indexOf(needed[i]);
      if (idx[i] < 0) {
        throw new IllegalStateException(
            "Colonne « "
                + needed[i]
                + " » absente de l'en-tête COPY "
                + table
                + " — format Dosleg modifié, parser à mettre à jour (note § 6).");
      }
    }
    return idx;
  }

  private static String field(String[] f, int i) {
    if (i < 0 || i >= f.length) {
      return null;
    }
    String v = f[i];
    if (v.equals("\\N")) {
      return null;
    }
    return unescape(v).trim();
  }

  /** Déséchappe les séquences PostgreSQL d'une valeur de champ COPY (best-effort sur les usuelles). */
  private static String unescape(String v) {
    if (v.indexOf('\\') < 0) {
      return v;
    }
    StringBuilder b = new StringBuilder(v.length());
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c == '\\' && i + 1 < v.length()) {
        char n = v.charAt(++i);
        switch (n) {
          case 't' -> b.append('\t');
          case 'n' -> b.append('\n');
          case 'r' -> b.append('\r');
          case '\\' -> b.append('\\');
          default -> b.append(n);
        }
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }

  private static String datePart(String dateTime) {
    if (dateTime == null) {
      return null;
    }
    return dateTime.length() >= 10 ? dateTime.substring(0, 10) : dateTime;
  }

  private static Integer asInt(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }
}

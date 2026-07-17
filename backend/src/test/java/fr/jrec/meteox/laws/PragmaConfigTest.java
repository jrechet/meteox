package fr.jrec.meteox.laws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que le pool Agroal ouvre bien chaque connexion SQLite en mode WAL avec un
 * busy_timeout : sans ça, le job sync-scrutins (écriture) et les lectures publiques de
 * /api/laws entrent en collision SQLITE_BUSY sur le conteneur mono-writer (issue #3).
 */
@QuarkusTest
class PragmaConfigTest {

  @Inject DataSource dataSource;

  @Test
  void connections_use_wal_journal_mode_and_busy_timeout() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      try (ResultSet rs = st.executeQuery("PRAGMA journal_mode;")) {
        assertEquals(true, rs.next());
        assertEquals("wal", rs.getString(1).toLowerCase());
      }
      try (ResultSet rs = st.executeQuery("PRAGMA busy_timeout;")) {
        assertEquals(true, rs.next());
        assertEquals(5000, rs.getInt(1));
      }
    }
  }
}

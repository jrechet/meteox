package fr.jrec.meteox.laws.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;

/** Sonde de vie : vérifie que l'application et la base SQLite répondent. */
@Path("/api/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

  @Inject DataSource dataSource;

  @GET
  public Response health() {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.execute("SELECT 1");
      return Response.ok(Map.of("status", "UP", "database", "UP")).build();
    } catch (SQLException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("status", "DOWN", "database", "DOWN"))
          .build();
    }
  }
}

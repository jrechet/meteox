package fr.jrec.meteox.laws.indicators.api;

import fr.jrec.meteox.laws.indicators.IndicatorExtractionException;
import fr.jrec.meteox.laws.indicators.IndicatorRepository;
import fr.jrec.meteox.laws.indicators.IndicatorScoringService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Endpoints admin du workflow draft → published (issue #4). Protégés par le secret
 * {@code meteox.admin.token} (env MX_ADMIN_TOKEN, jamais en dur) via l'en-tête X-Admin-Token —
 * sans token configuré, l'admin est fermé (503). La publication exige un relecteur humain
 * ({@code reviewedBy}) et laisse une piste d'audit consultable.
 */
@Path("/api/admin/indicators")
@Produces(MediaType.APPLICATION_JSON)
public class AdminIndicatorResource {

  private static final Logger LOG = Logger.getLogger(AdminIndicatorResource.class);

  @Inject IndicatorScoringService service;
  @Inject IndicatorRepository repository;

  @ConfigProperty(name = "meteox.admin.token")
  Optional<String> adminToken;

  /** Lance l'extraction IA pour une loi : crée des scores draft (jamais visibles côté public). */
  @POST
  @Path("/extract/{lawId}")
  public Response extract(
      @PathParam("lawId") String lawId, @HeaderParam("X-Admin-Token") String token) {
    requireAdmin(token);
    try {
      List<Long> ids = service.extractDrafts(lawId, "admin");
      return Response.status(Response.Status.CREATED).entity(new Created(ids)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(new Error(e.getMessage())).build();
    } catch (IndicatorExtractionException e) {
      LOG.warnf("Extraction rejetée pour %s : %s", lawId, e.getMessage());
      return Response.status(422) // Unprocessable Entity — hors de jakarta.ws.rs.core.Response.Status
          .entity(new Error(e.getMessage()))
          .build();
    }
  }

  /** Scores en attente de relecture humaine. */
  @GET
  @Path("/drafts")
  public Response drafts(@HeaderParam("X-Admin-Token") String token) {
    requireAdmin(token);
    return Response.ok(repository.findDrafts()).build();
  }

  /** Validation humaine : publie un score draft, avec relecteur et horodatage. */
  @POST
  @Path("/{scoreId}/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response publish(
      @PathParam("scoreId") long scoreId,
      @HeaderParam("X-Admin-Token") String token,
      PublishRequest request) {
    requireAdmin(token);
    try {
      String reviewedBy = request == null ? null : request.reviewedBy();
      return Response.ok(service.publish(scoreId, reviewedBy)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage())).build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.CONFLICT).entity(new Error(e.getMessage())).build();
    }
  }

  /** Piste d'audit consultable : qui a validé quoi, quand. */
  @GET
  @Path("/audit")
  public Response audit(@HeaderParam("X-Admin-Token") String token) {
    requireAdmin(token);
    return Response.ok(repository.findAudit()).build();
  }

  private void requireAdmin(String token) {
    String expected = adminToken.map(String::strip).orElse("");
    if (expected.isBlank()) {
      throw new WebApplicationException(
          Response.status(Response.Status.SERVICE_UNAVAILABLE)
              .entity(new Error("Admin fermé : meteox.admin.token non configuré"))
              .build());
    }
    if (token == null || !constantTimeEquals(expected, token)) {
      throw new WebApplicationException(
          Response.status(Response.Status.UNAUTHORIZED)
              .entity(new Error("Jeton admin invalide"))
              .build());
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return java.security.MessageDigest.isEqual(x, y);
  }

  record PublishRequest(String reviewedBy) {}

  record Created(List<Long> draftIds) {}

  record Error(String error) {}
}

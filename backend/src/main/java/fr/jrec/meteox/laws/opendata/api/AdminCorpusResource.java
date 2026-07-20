package fr.jrec.meteox.laws.opendata.api;

import fr.jrec.meteox.laws.opendata.CorpusSyncService;
import fr.jrec.meteox.laws.opendata.CorpusSyncService.Promotion;
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
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

/**
 * Validation humaine des candidats « lois votées » du corpus élargi (issue #3, tâche 3).
 * Protégé par le secret {@code meteox.admin.token} (env MX_ADMIN_TOKEN) via l'en-tête
 * X-Admin-Token, comme les autres admins. Aucun candidat ne devient une loi publique sans une
 * action ici — et chaque publication porte scrutin + dossier officiels vérifiés.
 */
@Path("/api/admin/corpus")
@Produces(MediaType.APPLICATION_JSON)
public class AdminCorpusResource {

  @Inject CorpusSyncService service;
  @Inject ManagedExecutor executor;

  @ConfigProperty(name = "meteox.admin.token")
  Optional<String> adminToken;

  /** Candidats détectés en attente de relecture (jamais publics tant que non promus). */
  @GET
  @Path("/candidates")
  public Response candidates(@HeaderParam("X-Admin-Token") String token) {
    requireAdmin(token);
    return Response.ok(service.candidatesForReview()).build();
  }

  /**
   * Déclenche une passe de détection en ARRIÈRE-PLAN (téléchargement + parcours de ~12 000
   * scrutins sur 2 législatures : 1-2 min) et rend la main immédiatement.
   */
  @POST
  @Path("/sync")
  public Response sync(@HeaderParam("X-Admin-Token") String token) {
    requireAdmin(token);
    if (service.isRunning()) {
      return Response.ok(new SyncStatus("running")).build();
    }
    executor.runAsync(service::syncAll);
    return Response.status(Response.Status.ACCEPTED).entity(new SyncStatus("started")).build();
  }

  /**
   * Publie un candidat en loi votée, avec le titre éditorial, la catégorie, le résumé et le
   * dossier officiel (URL + fragment) fournis par l'humain — tous requis (Golden Rule).
   */
  @POST
  @Path("/{uid}/promote")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response promote(
      @PathParam("uid") String uid,
      @HeaderParam("X-Admin-Token") String token,
      Promotion request) {
    requireAdmin(token);
    if (request == null
        || isBlank(request.title())
        || isBlank(request.category())
        || isBlank(request.summary())
        || isBlank(request.textUrl())
        || isBlank(request.textExpect())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new Error("title, category, summary, textUrl et textExpect sont requis"))
          .build();
    }
    try {
      String lawId = service.promote(uid, request);
      return Response.status(Response.Status.CREATED).entity(new Promoted(lawId)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(new Error(e.getMessage())).build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.CONFLICT).entity(new Error(e.getMessage())).build();
    }
  }

  /** Écarte un candidat non pertinent. */
  @POST
  @Path("/{uid}/reject")
  public Response reject(@PathParam("uid") String uid, @HeaderParam("X-Admin-Token") String token) {
    requireAdmin(token);
    service.reject(uid);
    return Response.noContent().build();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
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
          Response.status(Response.Status.UNAUTHORIZED).entity(new Error("Jeton admin invalide")).build());
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    return java.security.MessageDigest.isEqual(
        a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  record SyncStatus(String status) {}

  record Promoted(String lawId) {}

  record Error(String error) {}
}

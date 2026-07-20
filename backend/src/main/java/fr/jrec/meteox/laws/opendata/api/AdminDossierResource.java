package fr.jrec.meteox.laws.opendata.api;

import fr.jrec.meteox.laws.admin.AdminAuth;
import fr.jrec.meteox.laws.opendata.DossierSyncService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.context.ManagedExecutor;

/**
 * Validation humaine des candidats « prochains scrutins » (issue #3, tâche 2). Protégé par le
 * secret {@code meteox.admin.token} (env MX_ADMIN_TOKEN) via l'en-tête X-Admin-Token, comme
 * l'admin des indicateurs. Aucun candidat ne devient une carte publique sans une action ici.
 */
@Path("/api/admin/dossiers")
@Produces(MediaType.APPLICATION_JSON)
public class AdminDossierResource {

  @Inject DossierSyncService service;
  @Inject ManagedExecutor executor;
  @Inject AdminAuth adminAuth;

  /**
   * Candidats détectés en attente de relecture (jamais publics tant que non promus), enrichis de
   * leur initiateur et du soutien (cosignataires) agrégé par groupe, triés par importance.
   */
  @GET
  @Path("/candidates")
  public Response candidates(@HeaderParam("X-Admin-Token") String token) {
    adminAuth.require(token);
    return Response.ok(service.candidatesForReview()).build();
  }

  /**
   * Déclenche une passe de détection en ARRIÈRE-PLAN (le téléchargement + parcours de ~3000
   * dossiers prend 1-2 min) et rend la main immédiatement — sinon la requête HTTP expirerait.
   */
  @POST
  @Path("/sync")
  public Response sync(@HeaderParam("X-Admin-Token") String token) {
    adminAuth.require(token);
    if (service.isRunning()) {
      return Response.ok(new SyncStatus("running")).build();
    }
    executor.runAsync(service::syncAll);
    return Response.status(Response.Status.ACCEPTED).entity(new SyncStatus("started")).build();
  }

  /** Publie un candidat en carte « à venir », avec les champs éditoriaux fournis par l'humain. */
  @POST
  @Path("/{uid}/promote")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response promote(
      @PathParam("uid") String uid,
      @HeaderParam("X-Admin-Token") String token,
      PromoteRequest request) {
    adminAuth.require(token);
    if (request == null || request.category() == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new Error("category est requis"))
          .build();
    }
    try {
      String lawId =
          service.promote(uid, request.category(), request.summary(), request.sourceExpect());
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
    adminAuth.require(token);
    service.reject(uid);
    return Response.noContent().build();
  }

  record SyncStatus(String status) {}

  record PromoteRequest(String category, String summary, String sourceExpect) {}

  record Promoted(String lawId) {}

  record Error(String error) {}
}

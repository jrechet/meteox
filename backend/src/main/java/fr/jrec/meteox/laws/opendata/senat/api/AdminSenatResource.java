package fr.jrec.meteox.laws.opendata.senat.api;

import fr.jrec.meteox.laws.admin.AdminAuth;
import fr.jrec.meteox.laws.opendata.senat.SenatSyncService;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.context.ManagedExecutor;

/**
 * Déclenchement à la demande de la synchronisation Sénat (issue #3, tâche 4). Le job tourne aussi
 * en cron ({@code meteox.sync-senat.cron}) ; cet endpoint permet un rafraîchissement immédiat,
 * comme pour les dossiers et le corpus. Protégé par {@link AdminAuth} (connexion GitHub OU jeton).
 * La passe (téléchargement Dosleg ~16 Mo + résolution + agrégats) est lancée en ARRIÈRE-PLAN et
 * rend la main tout de suite (202) — sinon la requête HTTP expirerait.
 */
@Path("/api/admin/senat")
@Produces(MediaType.APPLICATION_JSON)
public class AdminSenatResource {

  @Inject SenatSyncService service;
  @Inject ManagedExecutor executor;
  @Inject AdminAuth adminAuth;

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

  record SyncStatus(String status) {}
}

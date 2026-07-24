package fr.jrec.meteox.laws.opendata.api;

import fr.jrec.meteox.laws.admin.AdminAuth;
import fr.jrec.meteox.laws.opendata.SupportNetworkRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Analyse réseau des soutiens (issue #33, prolongement) : qui cosigne quoi, avec qui — par bloc,
 * par groupe et par personne. Surface ADMIN uniquement (analyse exploratoire) : toute exposition
 * publique de ces lectures sera une décision éditoriale séparée.
 */
@Path("/api/admin/reseau")
@Produces(MediaType.APPLICATION_JSON)
public class AdminNetworkResource {

  /** Un lien entre groupes n'est montré qu'à partir de 2 dossiers partagés (bruit en dessous). */
  private static final int MIN_SHARED_DOSSIERS = 2;

  /** Un pont transpartisan n'est « systématique » qu'à partir de 2 textes cosignés. */
  private static final int MIN_PAIR_DOSSIERS = 2;

  private static final int MAX_PAIRS = 30;

  @Inject SupportNetworkRepository network;
  @Inject AdminAuth adminAuth;

  @GET
  public Response reseau(@HeaderParam("X-Admin-Token") String token) {
    adminAuth.require(token);
    return Response.ok(
            new Reseau(
                network.groupNodes(),
                network.groupLinks(MIN_SHARED_DOSSIERS),
                network.blocSupportMatrix(),
                network.crossGroupPairs(MIN_PAIR_DOSSIERS, MAX_PAIRS)))
        .build();
  }

  record Reseau(
      List<SupportNetworkRepository.GroupNode> groupes,
      List<SupportNetworkRepository.GroupLink> liens,
      List<SupportNetworkRepository.BlocSupport> soutienParBloc,
      List<SupportNetworkRepository.CrossGroupPair> pontsTranspartisans) {}
}

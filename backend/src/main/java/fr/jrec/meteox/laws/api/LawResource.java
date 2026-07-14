package fr.jrec.meteox.laws.api;

import fr.jrec.meteox.laws.model.Law;
import fr.jrec.meteox.laws.repository.LawRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Source de vérité des lois vérifiées. Seules les lois {@code published} sont exposées
 * (Golden Rule : aucune carte sans source officielle valide et concordante).
 */
@Path("/api/laws")
@Produces(MediaType.APPLICATION_JSON)
public class LawResource {

  @Inject LawRepository repository;

  @GET
  public List<Law> laws() {
    return repository.findPublished();
  }
}

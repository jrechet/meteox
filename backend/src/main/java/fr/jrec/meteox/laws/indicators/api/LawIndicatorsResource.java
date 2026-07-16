package fr.jrec.meteox.laws.indicators.api;

import fr.jrec.meteox.laws.indicators.IndicatorRepository;
import fr.jrec.meteox.laws.indicators.IndicatorScoreRow;
import fr.jrec.meteox.laws.repository.LawRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Transparence des indicateurs (issue #4) : pour chaque score PUBLIÉ, la justification citée, le
 * niveau de confiance, le modèle d'origine (null = éditorial) et la trace de relecture humaine.
 * Les scores {@code draft} ne sont jamais exposés ici ni dans GET /api/laws.
 *
 * <p>Contrat JSON attendu par le front (issue #5, info-bulle des jauges) :
 * {@code {"lawId": "...", "methodology": "<url>", "indicators": [{"indicator": "pesticides",
 * "score": 1, "justification": "...", "citation": "...", "confidence": "haute",
 * "model": "claude-api:claude-sonnet-5", "reviewedBy": "...", "reviewedAt": "..."}]}}
 */
@Path("/api/laws/{id}/indicators")
@Produces(MediaType.APPLICATION_JSON)
public class LawIndicatorsResource {

  static final String METHODOLOGY_URL =
      "https://github.com/jrechet/meteox/blob/main/docs/methodologie-indicateurs.md";

  @Inject LawRepository laws;
  @Inject IndicatorRepository indicators;

  @GET
  public Response indicators(@PathParam("id") String lawId) {
    boolean visible = laws.findById(lawId).map(law -> law.published()).orElse(false);
    if (!visible) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new Error("Loi inconnue ou dépubliée : " + lawId))
          .build();
    }
    return Response.ok(new Payload(lawId, METHODOLOGY_URL, indicators.findPublishedByLaw(lawId)))
        .build();
  }

  record Payload(String lawId, String methodology, List<IndicatorScoreRow> indicators) {}

  record Error(String error) {}
}

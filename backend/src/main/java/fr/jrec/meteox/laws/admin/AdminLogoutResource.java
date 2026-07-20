package fr.jrec.meteox.laws.admin;

import io.quarkus.oidc.OidcSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/**
 * Déconnexion <b>locale</b> de l'admin : efface le cookie de session OIDC via
 * {@link OidcSession#logout()} (contrairement à {@code quarkus.oidc.logout.path}, ceci n'exige
 * PAS d'{@code end_session_endpoint} — que GitHub n'expose pas). Puis redirige vers {@code
 * admin.html}, qui étant protégée renvoie l'utilisateur vers la mire de login GitHub.
 *
 * <p>La redirection est relative ({@code ../admin.html} depuis {@code /admin/logout}) pour rester
 * correcte derrière Traefik (préfixe {@code /meteox-laws-int} conservé par le navigateur).
 * {@link Instance} rend l'{@code OidcSession} optionnel : hors OIDC (tests/dev), on redirige sans
 * rien effacer.
 */
@Path("/admin/logout")
public class AdminLogoutResource {

  @Inject Instance<OidcSession> oidcSession;

  @GET
  public Uni<Response> logout() {
    Response redirect = Response.seeOther(URI.create("../admin.html")).build();
    if (oidcSession.isResolvable()) {
      return oidcSession.get().logout().replaceWith(redirect);
    }
    return Uni.createFrom().item(redirect);
  }
}

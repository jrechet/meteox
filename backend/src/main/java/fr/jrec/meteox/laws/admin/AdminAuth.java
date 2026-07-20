package fr.jrec.meteox.laws.admin;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Garde d'accès à l'admin, partagée par les trois surfaces admin (indicateurs #4, dossiers #3,
 * corpus #3). Deux voies acceptées :
 *
 * <ol>
 *   <li><b>Connexion GitHub OAuth</b> — l'humain se connecte via la « mire » GitHub ; son identité
 *       porte le rôle {@link #ADMIN_ROLE} si son login figure dans l'allowlist (cf.
 *       {@link AdminRoleAugmentor}).
 *   <li><b>Jeton X-Admin-Token</b> — voie de secours / automatisation (CI, scripts de sync), en
 *       comparaison à temps constant.
 * </ol>
 *
 * La page {@code admin.html} est, elle, protégée en amont par la connexion GitHub (redirection de
 * login) : un navigateur non connecté n'atteint jamais ces endpoints sans identité.
 */
@ApplicationScoped
public class AdminAuth {

  /** Rôle attribué à une identité GitHub autorisée (allowlist) — cf. {@link AdminRoleAugmentor}. */
  public static final String ADMIN_ROLE = "admin";

  @Inject SecurityIdentity identity;

  @ConfigProperty(name = "meteox.admin.token")
  Optional<String> adminToken;

  /**
   * Autorise la requête ou lève une {@link WebApplicationException} (401). Ordre : session GitHub
   * autorisée d'abord, sinon jeton de secours.
   */
  public void require(String token) {
    if (isAuthorizedGithubSession()) {
      return;
    }
    String expected = adminToken.map(String::strip).orElse("");
    if (token != null && !expected.isBlank() && constantTimeEquals(expected, token)) {
      return;
    }
    throw new WebApplicationException(
        Response.status(Response.Status.UNAUTHORIZED)
            .entity(new Error("Authentification requise : connexion GitHub ou jeton admin valide"))
            .build());
  }

  private boolean isAuthorizedGithubSession() {
    return identity != null && !identity.isAnonymous() && identity.hasRole(ADMIN_ROLE);
  }

  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  /** Corps d'erreur JSON, forme {@code {"error": "..."}} (identique aux resources historiques). */
  public record Error(String error) {}
}

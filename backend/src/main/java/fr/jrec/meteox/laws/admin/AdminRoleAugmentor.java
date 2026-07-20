package fr.jrec.meteox.laws.admin;

import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Locale;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Attribue le rôle {@link AdminAuth#ADMIN_ROLE} à une identité GitHub authentifiée dont le
 * {@code login} figure dans l'allowlist ({@code meteox.admin.github-logins}). Toute autre identité
 * (login hors liste, anonyme) reste sans rôle admin : elle pourra se connecter mais pas administrer.
 *
 * <p>Le login GitHub est lu depuis l'UserInfo OAuth (le provider GitHub de Quarkus le renseigne),
 * avec repli sur le nom du principal. La comparaison est insensible à la casse (les logins GitHub
 * ne sont pas sensibles à la casse).
 */
@ApplicationScoped
public class AdminRoleAugmentor implements SecurityIdentityAugmentor {

  @ConfigProperty(name = "meteox.admin.github-logins")
  List<String> allowedLogins;

  @Override
  public Uni<SecurityIdentity> augment(
      SecurityIdentity identity, AuthenticationRequestContext context) {
    if (identity.isAnonymous()) {
      return Uni.createFrom().item(identity);
    }
    String login = githubLogin(identity);
    if (login != null && isAllowed(login)) {
      return Uni.createFrom()
          .item(QuarkusSecurityIdentity.builder(identity).addRole(AdminAuth.ADMIN_ROLE).build());
    }
    return Uni.createFrom().item(identity);
  }

  private boolean isAllowed(String login) {
    String normalized = login.toLowerCase(Locale.ROOT);
    return allowedLogins.stream()
        .map(l -> l.strip().toLowerCase(Locale.ROOT))
        .anyMatch(normalized::equals);
  }

  /** Login GitHub depuis l'UserInfo OAuth, sinon le nom du principal. */
  private static String githubLogin(SecurityIdentity identity) {
    UserInfo userInfo = identity.getAttribute("userinfo");
    if (userInfo != null && userInfo.getString("login") != null) {
      return userInfo.getString("login");
    }
    return identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
  }
}

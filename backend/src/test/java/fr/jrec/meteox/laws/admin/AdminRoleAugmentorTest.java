package fr.jrec.meteox.laws.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Décision d'allowlist (issue OAuth admin) : seul un login GitHub listé reçoit le rôle admin.
 * Test unitaire pur (sans serveur OAuth) : on fournit une identité + son UserInfo et on vérifie
 * l'attribution du rôle.
 */
class AdminRoleAugmentorTest {

  private AdminRoleAugmentor augmentorFor(String... allowed) {
    var a = new AdminRoleAugmentor();
    a.allowedLogins = List.of(allowed);
    return a;
  }

  private SecurityIdentity githubIdentityWithLogin(String login) {
    UserInfo userInfo = new UserInfo("{\"login\":\"" + login + "\"}");
    return QuarkusSecurityIdentity.builder()
        .setPrincipal(new QuarkusPrincipal(login))
        .addAttribute("userinfo", userInfo)
        .build();
  }

  private SecurityIdentity augment(AdminRoleAugmentor augmentor, SecurityIdentity identity) {
    return augmentor.augment(identity, null).await().indefinitely();
  }

  @Test
  void grants_admin_role_to_allowlisted_login() {
    SecurityIdentity out = augment(augmentorFor("jrechet"), githubIdentityWithLogin("jrechet"));
    assertTrue(out.hasRole(AdminAuth.ADMIN_ROLE), "le login autorisé doit recevoir le rôle admin");
  }

  @Test
  void allowlist_is_case_insensitive() {
    // Les logins GitHub ne sont pas sensibles à la casse.
    SecurityIdentity out = augment(augmentorFor("JRechet"), githubIdentityWithLogin("jrechet"));
    assertTrue(out.hasRole(AdminAuth.ADMIN_ROLE));
  }

  @Test
  void denies_admin_role_to_login_outside_allowlist() {
    SecurityIdentity out = augment(augmentorFor("jrechet"), githubIdentityWithLogin("intrus"));
    assertFalse(out.hasRole(AdminAuth.ADMIN_ROLE), "un login hors liste ne doit pas être admin");
  }

  @Test
  void leaves_anonymous_identity_untouched() {
    SecurityIdentity anonymous = QuarkusSecurityIdentity.builder().setAnonymous(true).build();
    SecurityIdentity out = augment(augmentorFor("jrechet"), anonymous);
    assertFalse(out.hasRole(AdminAuth.ADMIN_ROLE));
    assertTrue(out.isAnonymous());
  }
}

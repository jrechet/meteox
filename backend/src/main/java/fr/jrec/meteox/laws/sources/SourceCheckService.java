package fr.jrec.meteox.laws.sources;

import fr.jrec.meteox.laws.github.GitHubNotifier;
import fr.jrec.meteox.laws.model.Law;
import fr.jrec.meteox.laws.repository.LawRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Job check-sources côté serveur : vérifie chaque URL officielle (statut HTTP + fragment
 * de titre attendu). Toute source invalide dépublie la loi (absente de GET /api/laws),
 * historise le contrôle dans source_checks et signale via une issue GitHub.
 */
@ApplicationScoped
public class SourceCheckService {

  private static final Logger LOG = Logger.getLogger(SourceCheckService.class);

  @Inject LawRepository repository;
  @Inject SourceChecker checker;
  @Inject GitHubNotifier notifier;

  public void checkAllPublished() {
    List<Law> laws = repository.findPublished();
    LOG.infof("check-sources : %d loi(s) publiée(s) à vérifier", laws.size());
    for (Law law : laws) {
      checkLaw(law);
    }
  }

  /** Vérifie les deux URLs d'une loi ; dépublie et signale si l'une est invalide. */
  public void checkLaw(Law law) {
    var failures = new ArrayList<String>();
    check(law, "sourceUrl", law.sourceUrl(), law.sourceExpect(), failures);
    check(law, "textUrl", law.textUrl(), law.textExpect(), failures);
    if (failures.isEmpty()) {
      LOG.infof("check-sources OK : %s", law.id());
      return;
    }
    repository.unpublish(law.id());
    String details = String.join("\n", failures);
    LOG.errorf("check-sources KO : %s dépubliée —%n%s", law.id(), details);
    notifier.reportInvalidSource(law.id(), law.title(), details);
  }

  private void check(Law law, String field, String url, String expect, List<String> failures) {
    SourceChecker.CheckResult result = checker.check(url, expect);
    repository.recordSourceCheck(law.id(), field, url, result.httpStatus(), result.ok(), result.reason());
    if (!result.ok()) {
      failures.add("- `" + field + "` " + url + " → " + result.reason());
    }
  }
}

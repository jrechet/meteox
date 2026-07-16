package fr.jrec.meteox.laws.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Signale une source invalide en créant (ou commentant) une issue GitHub via l'API REST.
 * Sans token configuré (meteox.github.token), le signalement est loggué et ignoré —
 * la dépublication de la loi, elle, a toujours lieu.
 */
@ApplicationScoped
public class GitHubNotifier {

  private static final Logger LOG = Logger.getLogger(GitHubNotifier.class);
  private static final String SOURCES_LABEL = "check-sources";
  private static final String SCRUTINS_LABEL = "sync-scrutins";
  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  @ConfigProperty(name = "meteox.github.api-base", defaultValue = "https://api.github.com")
  String apiBase;

  @ConfigProperty(name = "meteox.github.repo", defaultValue = "jrechet/meteox")
  String repo;

  @ConfigProperty(name = "meteox.github.token")
  Optional<String> token;

  @Inject ObjectMapper mapper;

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

  /**
   * Crée une issue "[check-sources] <lawId> : source invalide" ou commente l'issue
   * ouverte existante portant le même titre (label {@value #ISSUE_LABEL}).
   */
  public void reportInvalidSource(String lawId, String lawTitle, String details) {
    String body =
        "Le job serveur `check-sources` a dépublié la loi **"
            + lawTitle
            + "** (`"
            + lawId
            + "`).\n\n"
            + details
            + "\n\nGolden Rule (AGENTS.md) : aucune carte publiée avec une source morte ou non concordante.";
    report(SOURCES_LABEL, "[check-sources] " + lawId + " : source invalide", body, lawId);
  }

  /**
   * Signale un réalignement des votes sur l'open data officiel (job sync-scrutins) : les
   * votes en base divergeaient et ont été écrasés — relecture humaine a posteriori.
   */
  public void reportScrutinDivergence(String lawId, String lawTitle, String details) {
    String body =
        "Le job serveur `sync-scrutins` a réaligné les votes de la loi **"
            + lawTitle
            + "** (`"
            + lawId
            + "`) sur le décompte officiel de l'open data AN.\n\n"
            + details
            + "\n\nPolitique actée (issue #3) : l'open data fait foi ; l'ancien décompte est"
            + " archivé dans `scrutin_syncs`. Merci de relire la page officielle du scrutin.";
    report(
        SCRUTINS_LABEL, "[sync-scrutins] " + lawId + " : votes réalignés sur l'open data", body,
        lawId);
  }

  /** Crée une issue portant {@code issueTitle} ou commente l'issue ouverte de même titre. */
  private void report(String label, String issueTitle, String body, String lawId) {
    if (token.isEmpty() || token.get().isBlank()) {
      LOG.warnf(
          "Pas de token GitHub configuré (meteox.github.token) — issue non créée pour %s", lawId);
      return;
    }
    try {
      Optional<Integer> existing = findOpenIssue(label, issueTitle);
      if (existing.isPresent()) {
        post("/repos/" + repo + "/issues/" + existing.get() + "/comments", commentPayload(body));
        LOG.infof("Commentaire ajouté à l'issue GitHub #%d pour %s", existing.get(), lawId);
      } else {
        post("/repos/" + repo + "/issues", issuePayload(label, issueTitle, body));
        LOG.infof("Issue GitHub créée pour %s", lawId);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.errorf("Signalement GitHub interrompu pour %s", lawId);
    } catch (Exception e) {
      LOG.errorf(e, "Signalement GitHub impossible pour %s", lawId);
    }
  }

  private Optional<Integer> findOpenIssue(String label, String issueTitle) throws Exception {
    String url =
        apiBase
            + "/repos/"
            + repo
            + "/issues?state=open&labels="
            + URLEncoder.encode(label, StandardCharsets.UTF_8)
            + "&per_page=100";
    HttpResponse<String> response =
        client.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      LOG.warnf("Listing des issues GitHub en échec (HTTP %d)", response.statusCode());
      return Optional.empty();
    }
    for (JsonNode issue : mapper.readTree(response.body())) {
      if (issueTitle.equals(issue.path("title").asText())) {
        return Optional.of(issue.path("number").asInt());
      }
    }
    return Optional.empty();
  }

  private void post(String path, String payload) throws Exception {
    HttpResponse<String> response =
        client.send(
            request(apiBase + path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 300) {
      throw new IllegalStateException(
          "GitHub API " + path + " → HTTP " + response.statusCode() + " : " + response.body());
    }
  }

  private HttpRequest.Builder request(String url) {
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(TIMEOUT)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Authorization", "Bearer " + token.orElseThrow());
  }

  private String issuePayload(String label, String title, String body) throws Exception {
    ObjectNode node = mapper.createObjectNode();
    node.put("title", title);
    node.put("body", body);
    node.putArray("labels").add(label);
    return mapper.writeValueAsString(node);
  }

  private String commentPayload(String body) throws Exception {
    ObjectNode node = mapper.createObjectNode();
    node.put("body", body);
    return mapper.writeValueAsString(node);
  }
}

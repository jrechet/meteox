package fr.jrec.meteox.laws.opendata;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.jrec.meteox.laws.github.GitHubNotifier;
import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.model.Law;
import fr.jrec.meteox.laws.opendata.ScrutinExtractionService.ScrutinExtraction;
import fr.jrec.meteox.laws.repository.LawRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Synchronise les votes des lois publiées avec le décompte officiel de l'open data AN
 * (issue #3, tâche 1). Politique actée : <b>l'open data fait foi</b> — en cas de divergence
 * les votes sont écrasés, l'ancien décompte est archivé dans {@code scrutin_syncs} et une
 * issue GitHub est ouverte pour relecture humaine a posteriori. Un scrutin en échec
 * n'interrompt jamais la synchronisation des autres.
 */
@ApplicationScoped
public class ScrutinSyncService {

  private static final Logger LOG = Logger.getLogger(ScrutinSyncService.class);

  @Inject LawRepository repository;
  @Inject OpenDataScrutins openData;
  @Inject ScrutinExtractionService extraction;
  @Inject GitHubNotifier notifier;
  @Inject ObjectMapper mapper;

  /** Bilan d'une passe de synchronisation. */
  public record SyncReport(int synced, int changed, int failed) {}

  public SyncReport syncAll() {
    int synced = 0;
    int changed = 0;
    int failed = 0;
    for (Law law : repository.findPublished()) {
      Optional<ScrutinRef> ref = ScrutinRef.fromUrl(law.sourceUrl());
      if (ref.isEmpty()) {
        continue; // loi sans scrutin associé (ex. upcoming) : hors périmètre du job
      }
      try {
        boolean updated = syncLaw(law, ref.get());
        synced++;
        if (updated) {
          changed++;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.errorf("Synchronisation interrompue (loi %s)", law.id());
        return new SyncReport(synced, changed, failed + 1);
      } catch (Exception e) {
        failed++;
        LOG.errorf(e, "Synchronisation impossible pour la loi %s (%s)", law.id(), law.sourceUrl());
      }
    }
    LOG.infof(
        "sync-scrutins : %d scrutin(s) synchronisé(s), %d divergence(s) corrigée(s), %d échec(s)",
        synced, changed, failed);
    return new SyncReport(synced, changed, failed);
  }

  /** Rend {@code true} si les votes en base ont été réalignés sur l'open data. */
  boolean syncLaw(Law law, ScrutinRef ref) throws Exception {
    byte[] json =
        openData
            .scrutinJson(ref)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Scrutin " + ref.fileName() + " absent du jeu open data"));
    ScrutinExtraction extracted = extraction.extract(new ByteArrayInputStream(json));
    Map<String, BlocVotes> current = repository.votesFor(law.id());
    String newJson = mapper.writeValueAsString(extracted.votesByBloc());

    if (extracted.votesByBloc().equals(current)) {
      repository.recordScrutinSync(
          law.id(), ref.legislature(), ref.numero(), extracted.scrutinUrl(), false, null, newJson);
      return false;
    }

    String oldJson = mapper.writeValueAsString(current);
    repository.replaceVotes(law.id(), extracted.votesByBloc());
    repository.recordScrutinSync(
        law.id(), ref.legislature(), ref.numero(), extracted.scrutinUrl(), true, oldJson, newJson);
    LOG.warnf(
        "Votes de %s réalignés sur l'open data (scrutin %d/%d) : %s → %s",
        law.id(), ref.legislature(), ref.numero(), oldJson, newJson);
    notifier.reportScrutinDivergence(
        law.id(),
        law.title(),
        "Scrutin officiel : "
            + extracted.scrutinUrl()
            + "\n\nAncien décompte (base) :\n```json\n"
            + oldJson
            + "\n```\nNouveau décompte (open data) :\n```json\n"
            + newJson
            + "\n```");
    return true;
  }
}

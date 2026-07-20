package fr.jrec.meteox.laws.opendata;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Planification quotidienne de la détection du corpus des lois votées (cron configurable). */
@ApplicationScoped
public class CorpusSyncJob {

  @Inject CorpusSyncService service;

  @Scheduled(
      cron = "{meteox.sync-corpus.cron}",
      identity = "sync-corpus",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void run() {
    service.syncAll();
  }
}

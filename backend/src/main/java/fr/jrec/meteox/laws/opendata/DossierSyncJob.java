package fr.jrec.meteox.laws.opendata;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Planification quotidienne de la détection des dossiers « à venir » (cron configurable). */
@ApplicationScoped
public class DossierSyncJob {

  @Inject DossierSyncService service;

  @Scheduled(
      cron = "{meteox.sync-dossiers.cron}",
      identity = "sync-dossiers",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void run() {
    service.syncAll();
  }
}

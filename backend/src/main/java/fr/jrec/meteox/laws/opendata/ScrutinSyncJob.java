package fr.jrec.meteox.laws.opendata;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Planification quotidienne de la synchronisation des scrutins open data (cron configurable). */
@ApplicationScoped
public class ScrutinSyncJob {

  @Inject ScrutinSyncService service;

  @Scheduled(cron = "{meteox.sync-scrutins.cron}", identity = "sync-scrutins")
  void run() {
    service.syncAll();
  }
}

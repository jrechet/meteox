package fr.jrec.meteox.laws.opendata;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Planification quotidienne de la synchronisation des scrutins open data (cron configurable). */
@ApplicationScoped
public class ScrutinSyncJob {

  @Inject ScrutinSyncService service;

  // SKIP : une passe de synchronisation peut dépasser 24h en cas de lenteur/retry réseau ;
  // évite deux exécutions concurrentes qui écriraient en base en même temps.
  @Scheduled(
      cron = "{meteox.sync-scrutins.cron}",
      identity = "sync-scrutins",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void run() {
    service.syncAll();
  }
}

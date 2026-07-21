package fr.jrec.meteox.laws.opendata.senat;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Planification quotidienne de la synchronisation des scrutins Sénat (cron configurable). */
@ApplicationScoped
public class SenatSyncJob {

  @Inject SenatSyncService service;

  // SKIP : une passe télécharge/parse plusieurs jeux open data (Dosleg ~126 Mo) et peut être longue ;
  // évite deux exécutions concurrentes qui écriraient la facette Sénat en même temps.
  @Scheduled(
      cron = "{meteox.sync-senat.cron}",
      identity = "sync-senat",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void run() {
    service.syncAll();
  }
}

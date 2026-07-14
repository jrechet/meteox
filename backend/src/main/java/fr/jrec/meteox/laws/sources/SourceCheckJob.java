package fr.jrec.meteox.laws.sources;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Planification quotidienne du contrôle des sources (cron configurable). */
@ApplicationScoped
public class SourceCheckJob {

  @Inject SourceCheckService service;

  @Scheduled(cron = "{meteox.check-sources.cron}", identity = "check-sources")
  void run() {
    service.checkAllPublished();
  }
}

package fr.jrec.meteox.laws.opendata.senat;

import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.model.Law;
import fr.jrec.meteox.laws.repository.LawRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.logging.Logger;

/**
 * Synchronise la facette Sénat des lois publiées avec l'open data du Sénat (Dosleg + scrutins JSON
 * + ODSEN_HISTOGROUPES). L'open data fait foi ; un échec sur une loi n'interrompt jamais les
 * autres ; garde anti-concurrence (une passe télécharge/parse plusieurs jeux). Pour chaque loi :
 * résolution vers son scrutin « sur l'ensemble » (règle CMP/dernière lecture), agrégation par bloc,
 * persistance — ou marqueur « pas de scrutin public » (voté à main levée, cas PFAS).
 */
@ApplicationScoped
public class SenatSyncService {

  private static final Logger LOG = Logger.getLogger(SenatSyncService.class);
  static final String NO_SCRUTIN_REASON = "Voté à main levée — pas de scrutin public au Sénat";

  @Inject LawRepository laws;
  @Inject SenatRepository senat;
  @Inject OpenDataDosleg dosleg;
  @Inject OpenDataHistoGroupes histoGroupes;
  @Inject OpenDataSenatScrutin scrutins;
  @Inject SenatScrutinResolver resolver;
  @Inject SenatScrutinVotes votes;

  private final AtomicBoolean running = new AtomicBoolean(false);

  /** Bilan d'une passe : lois scannées, scrutins publiés, « à main levée », échecs. */
  public record SyncReport(int scanned, int withScrutin, int noScrutin, int failed) {}

  public boolean isRunning() {
    return running.get();
  }

  public SyncReport syncAll() {
    if (!running.compareAndSet(false, true)) {
      LOG.info("sync-senat : une passe est déjà en cours, celle-ci est ignorée");
      return new SyncReport(0, 0, 0, 0);
    }
    int scanned = 0;
    int withScrutin = 0;
    int noScrutin = 0;
    int failed = 0;
    var histoHolder = new HistoGroupes[1]; // chargé paresseusement (seulement si un scrutin à agréger)
    try {
      DoslegDataset dataset = dosleg.dataset();
      for (Law law : laws.findPublished()) {
        scanned++;
        try {
          SenatResolution r = resolver.resolve(law.textUrl(), dataset);
          switch (r.kind()) {
            case RESOLVED -> {
              if (syncScrutin(law, r.scrutin(), histoHolder)) {
                withScrutin++;
              }
            }
            case NO_PUBLIC_SCRUTIN -> {
              senat.saveNoPublicScrutin(law.id(), NO_SCRUTIN_REASON);
              noScrutin++;
            }
            case UNRESOLVED -> {
              /* aucune loi Dosleg appariée : on laisse la facette telle quelle (jamais de perte). */
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          LOG.errorf("Synchronisation Sénat interrompue (loi %s)", law.id());
          return new SyncReport(scanned, withScrutin, noScrutin, failed + 1);
        } catch (Exception e) {
          failed++;
          LOG.errorf(e, "Synchronisation Sénat impossible pour la loi %s (%s)", law.id(), e.getMessage());
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Synchronisation Sénat interrompue (chargement Dosleg)");
    } catch (Exception e) {
      LOG.errorf(e, "Open data Sénat (Dosleg) indisponible — passe abandonnée (%s)", e.getMessage());
    } finally {
      running.set(false);
    }
    LOG.infof(
        "sync-senat : %d loi(s) scannée(s), %d scrutin(s) public(s), %d à main levée, %d échec(s)",
        scanned, withScrutin, noScrutin, failed);
    return new SyncReport(scanned, withScrutin, noScrutin, failed);
  }

  /** Rend {@code true} si les votes Sénat de la loi ont été (ré)écrits depuis l'open data. */
  private boolean syncScrutin(Law law, SenatScrutinRef ref, HistoGroupes[] histoHolder)
      throws Exception {
    Optional<byte[]> json = scrutins.scrutinJson(ref.session(), ref.numero());
    if (json.isEmpty()) {
      LOG.warnf(
          "Scrutin Sénat %d-%d introuvable pour la loi %s — facette inchangée",
          ref.session(), ref.numero(), law.id());
      return false;
    }
    LocalDate date = parseDate(ref.scrutinDate());
    if (date == null) {
      LOG.warnf("Date de scrutin Sénat illisible (%s) pour la loi %s — facette inchangée", ref.scrutinDate(), law.id());
      return false;
    }
    if (histoHolder[0] == null) {
      histoHolder[0] = histoGroupes.index();
    }
    Map<String, BlocVotes> byBloc =
        votes.aggregate(json.get(), date, histoHolder[0], ref.session(), ref.numero());
    senat.saveScrutin(law.id(), ref, byBloc);
    LOG.infof(
        "Facette Sénat de %s : scrutin %d-%d (%s) agrégé par bloc",
        law.id(), ref.session(), ref.numero(), ref.scrutinUrl());
    return true;
  }

  private static LocalDate parseDate(String iso) {
    if (iso == null || iso.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(iso.trim());
    } catch (Exception e) {
      return null;
    }
  }
}

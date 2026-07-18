package fr.jrec.meteox.laws.opendata;

import fr.jrec.meteox.laws.opendata.DossierParser.ParsedDossier;
import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.Aggregate;
import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.Signataire;
import fr.jrec.meteox.laws.repository.LawRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Détecte dans l'open data AN les dossiers 17e à thème environnemental encore en cours et les
 * verse dans la table de staging {@code dossier_candidates} (issue #3, tâche 2). RIEN n'est
 * publié ici : la promotion en carte {@code upcoming} passe par une validation humaine (le flux
 * brut est trop bruité). Sécurité : dès qu'un dossier promu devient promulgué, la loi upcoming
 * correspondante est dépubliée (« une loi promulguée ne reste jamais à venir »).
 */
@ApplicationScoped
public class DossierSyncService {

  private static final Logger LOG = Logger.getLogger(DossierSyncService.class);

  // Mots-clés thématiques (frontières de mot, sur titre normalisé sans accents). Volontairement
  // large : le tri fin est fait par la validation humaine, pas par ce filtre grossier.
  private static final Pattern THEME =
      Pattern.compile(
          "\\b(eaux?|pesticides?|glyphosate|pfas|climat\\w*|canicul\\w*|energ\\w*|renouvelabl\\w*"
              + "|carbone|agricol\\w*|agricultur\\w*|pesticide\\w*|biodiversit\\w*|environnement\\w*"
              + "|pollution\\w*|ecologi\\w*|nappe\\w*|zones? humides?|littoral\\w*|forets?)\\b");

  @Inject OpenDataDossiers openData;
  @Inject DossierRepository candidates;
  @Inject DossierSignataireRepository signataires;
  @Inject SignataireResolver signataireResolver;
  @Inject LawRepository laws;

  @ConfigProperty(name = "meteox.sync-dossiers.legislature", defaultValue = "17")
  int legislature;

  private final java.util.concurrent.atomic.AtomicBoolean running =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  /** Bilan d'une passe de synchronisation. */
  public record SyncReport(int scanned, int candidates, int demoted) {}

  /** Une passe est-elle en cours ? (le téléchargement + parcours prend ~1-2 min). */
  public boolean isRunning() {
    return running.get();
  }

  public SyncReport syncAll() {
    if (!running.compareAndSet(false, true)) {
      LOG.info("sync-dossiers : une passe est déjà en cours, celle-ci est ignorée");
      return new SyncReport(0, 0, 0);
    }
    var stats = new int[4]; // [scanned, candidates, demoted, removed]
    var seen = new java.util.HashSet<String>();
    try {
      openData.forEachDossier(
          legislature,
          d -> {
            stats[0]++;
            // Sécurité d'abord (indépendante du filtre thématique) : un dossier promulgué ne
            // doit jamais rester une carte « à venir » — on dépublie la loi promue le cas échéant.
            if (d.promulgated() && demoteIfPromoted(d)) {
              stats[2]++;
            }
            // On ne garde que les projets/propositions de LOI (pas les résolutions, rapports,
            // pétitions… qui ne sont pas des textes de loi et n'ont rien à faire en « à venir »).
            if (!isLaw(d.procedure())) {
              return;
            }
            Optional<String> theme = matchTheme(d.titre());
            if (theme.isEmpty()) {
              return;
            }
            stats[1]++;
            seen.add(d.uid());
            candidates.upsert(
                d.uid(), d.legislature(), d.titre(), d.url(), theme.get(), d.promulgated(),
                d.procedure(), isProjetDeLoi(d.procedure()));
            // Signal d'importance : auteur + cosignataires du texte déposé, best-effort. Une
            // résolution qui échoue (document absent, réseau, référentiel indisponible) ne doit
            // JAMAIS interrompre le scan — on garde le candidat, sans ses signataires.
            resolveSignatairesQuietly(d);
          });
      // Réconciliation (seulement après un scan RÉUSSI, pour ne jamais vider la liste sur une
      // panne réseau) : retire les candidats non actionnés qui ne matchent plus, et les
      // signataires des dossiers qui ne sont plus revus (jamais d'orphelins).
      stats[3] = candidates.deleteUnactionedNotIn(seen);
      signataires.deleteForDossiersNotIn(seen);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Synchronisation des dossiers interrompue");
    } catch (Exception e) {
      LOG.errorf(e, "Synchronisation des dossiers impossible (%s)", e.getMessage());
    } finally {
      running.set(false);
    }
    LOG.infof(
        "sync-dossiers : %d scanné(s), %d candidat(s), %d dépublié(s), %d retiré(s)",
        stats[0], stats[1], stats[2], stats[3]);
    return new SyncReport(stats[0], stats[1], stats[2]);
  }

  /**
   * Résout et stocke les signataires du texte déposé d'un candidat, sans jamais laisser une erreur
   * remonter dans la boucle de scan. Ne fait rien si le dossier n'a pas de document de dépôt.
   */
  private void resolveSignatairesQuietly(ParsedDossier d) {
    if (d.depotDocumentRef() == null) {
      return;
    }
    try {
      List<Signataire> resolved = signataireResolver.resolve(d.legislature(), d.depotDocumentRef());
      signataires.replaceForDossier(d.uid(), resolved);
    } catch (RuntimeException e) {
      LOG.warnf("Signataires du dossier %s non enregistrés (%s) — candidat conservé", d.uid(), e.getMessage());
    }
  }

  /**
   * Candidats en attente de relecture, enrichis de leur initiateur et du soutien (cosignataires)
   * agrégé par groupe politique (issue #33, sous-issue D). Tri par importance : les projets de loi
   * (gouvernement) d'abord, puis les textes les plus soutenus (nombre de cosignataires décroissant).
   * L'agrégat est calculé en UNE requête groupée (pas de N+1).
   */
  public List<CandidateView> candidatesForReview() {
    List<DossierRepository.Candidate> raw = candidates.listByStatus("candidate");
    Map<String, Aggregate> aggregates =
        signataires.aggregate(raw.stream().map(DossierRepository.Candidate::uid).toList());
    return raw.stream()
        .map(c -> toView(c, aggregates.getOrDefault(c.uid(), Aggregate.empty())))
        .sorted(
            Comparator.comparing(CandidateView::projetDeLoi)
                .reversed()
                .thenComparing(Comparator.comparingInt(CandidateView::cosignatairesTotal).reversed()))
        .toList();
  }

  private static CandidateView toView(DossierRepository.Candidate c, Aggregate agg) {
    CandidateView.Auteur auteur = null;
    if (agg.auteur() != null) {
      auteur = new CandidateView.Auteur(agg.auteur().nom(), agg.auteur().groupeSigle(), agg.auteur().bloc());
    }
    return new CandidateView(
        c.uid(), c.legislature(), c.titre(), c.dossierUrl(), c.theme(), c.procedure(),
        c.projetDeLoi(), c.terminated(), c.status(), c.promotedLawId(),
        auteur, agg.cosignatairesTotal(), agg.cosignatairesParGroupe());
  }

  /**
   * Validation humaine : promeut un candidat en carte {@code upcoming} publiée (issue #3).
   * L'humain fournit les champs éditoriaux (catégorie, résumé, date prévue, fragment attendu
   * pour la vérification de source). Rend l'identifiant de la loi créée.
   */
  public String promote(
      String uid, String category, String date, String summary, String sourceExpect) {
    DossierRepository.Candidate c =
        candidates
            .findByUid(uid)
            .orElseThrow(() -> new IllegalArgumentException("Candidat inconnu : " + uid));
    if (c.terminated()) {
      throw new IllegalStateException("Dossier promulgué/clos : ne peut pas devenir « à venir »");
    }
    if ("promoted".equals(c.status())) {
      throw new IllegalStateException("Candidat déjà promu : " + uid);
    }
    String fragment = (sourceExpect == null || sourceExpect.isBlank()) ? c.titre() : sourceExpect;
    laws.insertUpcoming(
        uid, c.titre(), category, date, summary, c.dossierUrl(), fragment, c.dossierUrl(), fragment);
    candidates.markPromoted(uid, uid);
    LOG.infof("Candidat %s promu en carte upcoming (catégorie %s)", uid, category);
    return uid;
  }

  /** Écarte un candidat non pertinent (il ne sera plus proposé à la relecture). */
  public void reject(String uid) {
    candidates.markRejected(uid);
  }

  /** Dépublie la carte upcoming issue d'un dossier désormais promulgué. Vrai si une loi a été dépubliée. */
  private boolean demoteIfPromoted(ParsedDossier d) {
    Optional<String> lawId = candidates.promotedLawId(d.uid());
    if (lawId.isEmpty()) {
      return false;
    }
    laws.unpublish(lawId.get());
    candidates.markTerminated(d.uid());
    LOG.warnf("Dossier %s promulgué : carte upcoming %s dépubliée", d.uid(), lawId.get());
    return true;
  }

  /** Vrai si la procédure est un projet OU une proposition de LOI (et non une résolution/rapport). */
  static boolean isLaw(String procedure) {
    return normalize(procedure).contains("loi");
  }

  /** Vrai si c'est un PROJET de loi (origine gouvernementale) — pas une proposition parlementaire. */
  static boolean isProjetDeLoi(String procedure) {
    return normalize(procedure).startsWith("projet de loi");
  }

  /** Premier mot-clé thématique présent dans le titre (comparaison sans accents), s'il y en a un. */
  static Optional<String> matchTheme(String titre) {
    Matcher m = THEME.matcher(normalize(titre));
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
  }

  private static String normalize(String s) {
    String noAccents =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    return noAccents.toLowerCase(Locale.FRENCH);
  }
}

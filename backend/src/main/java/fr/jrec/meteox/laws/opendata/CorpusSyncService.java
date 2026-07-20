package fr.jrec.meteox.laws.opendata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.jrec.meteox.laws.model.BlocVotes;
import fr.jrec.meteox.laws.opendata.ScrutinExtractionService.ScrutinExtraction;
import fr.jrec.meteox.laws.opendata.ScrutinParser.ParsedScrutin;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Élargissement du corpus des lois votées (issue #3, tâche 3). Détecte dans le jeu Scrutins de
 * l'open data AN les votes sur l'ensemble d'un texte de loi — scrutins solennels ET ordinaires
 * (la loi PFAS du corpus initial fut un scrutin ordinaire) — à thème environnemental, adoptés
 * depuis 2023, hors lois déjà au corpus, et les verse dans la table de staging
 * {@code law_candidates}. RIEN n'est publié ici : la promotion en loi {@code passed} passe par
 * une validation humaine qui fournit le titre éditorial et le dossier officiel vérifié
 * (Golden Rule : chaque publication porte scrutin + dossier vérifiés).
 */
@ApplicationScoped
public class CorpusSyncService {

  private static final Logger LOG = Logger.getLogger(CorpusSyncService.class);

  @Inject OpenDataScrutins openData;
  @Inject ScrutinExtractionService extraction;
  @Inject LawCandidateRepository candidates;
  @Inject fr.jrec.meteox.laws.repository.LawRepository laws;
  @Inject ObjectMapper mapper;

  /** Fenêtre de détection 2023-2026 (critère d'acceptance issue #3, tâche 3). */
  @ConfigProperty(name = "meteox.sync-corpus.min-date", defaultValue = "2023-01-01")
  String minDate;

  @ConfigProperty(name = "meteox.sync-corpus.legislatures", defaultValue = "16,17")
  List<Integer> legislatures;

  private final java.util.concurrent.atomic.AtomicBoolean running =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  /** Bilan d'une passe de détection. */
  public record SyncReport(int scanned, int candidates, int failed) {}

  /** Champs éditoriaux fournis par l'humain à la promotion d'un candidat. */
  public record Promotion(
      String title,
      String category,
      String summary,
      String sourceExpect,
      String textUrl,
      String textExpect) {}

  /** Une passe est-elle en cours ? (téléchargement + parcours de ~12 000 scrutins : 1-2 min). */
  public boolean isRunning() {
    return running.get();
  }

  public SyncReport syncAll() {
    if (!running.compareAndSet(false, true)) {
      LOG.info("sync-corpus : une passe est déjà en cours, celle-ci est ignorée");
      return new SyncReport(0, 0, 0);
    }
    var stats = new int[4]; // [scanned, candidates, failed, removed]
    var seen = new java.util.HashSet<String>();
    try {
      // Les scrutins des lois déjà au corpus (seed vérifié ou promotions passées) ne sont
      // jamais re-proposés : leur URL officielle est déjà un source_url de la table laws.
      Set<String> covered = laws.sourceUrls();
      for (int legislature : legislatures) {
        openData.forEachScrutin(legislature, s -> stage(s, covered, seen, stats));
      }
      // Réconciliation (seulement après un scan RÉUSSI de toutes les législatures, pour ne
      // jamais vider la liste sur une panne réseau) : retire les candidats non actionnés qui
      // ne matchent plus. Les promus/rejetés sont préservés (décision humaine).
      stats[3] = candidates.deleteUnactionedNotIn(seen);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Détection du corpus des lois votées interrompue");
    } catch (Exception e) {
      LOG.errorf(e, "Détection du corpus des lois votées impossible (%s)", e.getMessage());
    } finally {
      running.set(false);
    }
    LOG.infof(
        "sync-corpus : %d scanné(s), %d candidat(s), %d échec(s), %d retiré(s)",
        stats[0], stats[1], stats[2], stats[3]);
    return new SyncReport(stats[0], stats[1], stats[2]);
  }

  /** Applique les filtres à un scrutin et le verse en staging s'il est candidat. */
  private void stage(ParsedScrutin s, Set<String> covered, Set<String> seen, int[] stats) {
    stats[0]++;
    // On ne retient que les lois VOTÉES : le statut public est 'passed', un texte rejeté
    // n'a pas sa place au corpus.
    if (!isFinalLawVote(s) || !"adopté".equals(s.sortCode())) {
      return;
    }
    if (s.dateScrutin().compareTo(minDate) < 0) {
      return;
    }
    Optional<String> theme = ThemeFilter.matchTheme(s.titre());
    if (theme.isEmpty()) {
      return;
    }
    try {
      // Agrégation par bloc (Golden Rule : un organeRef inconnu fait échouer CE scrutin,
      // loggé et compté — jamais de total faussé silencieusement, jamais de scan interrompu).
      ScrutinExtraction x = extraction.aggregate(s);
      if (covered.contains(x.scrutinUrl())) {
        return; // loi déjà au corpus (seed vérifié ou promotion passée)
      }
      stats[1]++;
      seen.add(s.uid());
      candidates.upsert(
          s.uid(), s.legislature(), s.numero(), s.titre(), s.dateScrutin(), theme.get(),
          x.scrutinUrl(), votesJson(x.votesByBloc()));
    } catch (RuntimeException e) {
      stats[2]++;
      LOG.warnf("Scrutin %s écarté du corpus (%s)", s.uid(), e.getMessage());
    }
  }

  /**
   * Candidats en attente de relecture, votes par bloc désérialisés pour la page admin
   * (jamais publics tant que non promus).
   */
  public List<LawCandidateView> candidatesForReview() {
    return candidates.listByStatus("candidate").stream().map(this::toView).toList();
  }

  /**
   * Validation humaine : promeut un candidat en loi {@code passed} publiée, avec ses votes par
   * bloc et l'URL officielle du scrutin. L'humain fournit le titre éditorial, la catégorie, le
   * résumé et le dossier officiel (URL + fragment attendu) — check-sources vérifiera les deux
   * URLs en contenu. Rend l'identifiant de la loi créée.
   */
  public String promote(String uid, Promotion p) {
    LawCandidateRepository.Candidate c =
        candidates
            .findByUid(uid)
            .orElseThrow(() -> new IllegalArgumentException("Candidat inconnu : " + uid));
    if ("promoted".equals(c.status())) {
      throw new IllegalStateException("Candidat déjà promu : " + uid);
    }
    String fragment = (p.sourceExpect() == null || p.sourceExpect().isBlank())
        ? c.titre()
        : p.sourceExpect();
    laws.insertPassed(
        uid, p.title(), p.category(), c.dateScrutin(), p.summary(), c.scrutinUrl(), fragment,
        p.textUrl(), p.textExpect());
    laws.replaceVotes(uid, parseVotes(c.votesJson()));
    candidates.markPromoted(uid, uid);
    LOG.infof("Candidat %s promu en loi votée publiée (catégorie %s)", uid, p.category());
    return uid;
  }

  /** Écarte un candidat non pertinent (il ne sera plus proposé à la relecture). */
  public void reject(String uid) {
    candidates.markRejected(uid);
  }

  /**
   * Vrai pour un vote sur l'ENSEMBLE d'un texte de loi : scrutin solennel, ou scrutin ordinaire
   * portant sur « l'ensemble » (les votes d'articles ou d'amendements sont exclus). Le titre
   * doit mentionner une loi (les résolutions sont exclues).
   */
  static boolean isFinalLawVote(ParsedScrutin s) {
    String titre = ThemeFilter.normalize(s.titre());
    if (!titre.contains("loi")) {
      return false;
    }
    return ThemeFilter.normalize(s.typeVote()).contains("solennel")
        || titre.startsWith("l'ensemble");
  }

  private LawCandidateView toView(LawCandidateRepository.Candidate c) {
    return new LawCandidateView(
        c.uid(), c.legislature(), c.numero(), c.titre(), c.dateScrutin(), c.theme(),
        c.scrutinUrl(), parseVotes(c.votesJson()), c.status(), c.promotedLawId());
  }

  private String votesJson(Map<String, BlocVotes> votes) {
    try {
      return mapper.writeValueAsString(votes);
    } catch (Exception e) {
      throw new IllegalStateException("Sérialisation des votes impossible", e);
    }
  }

  private Map<String, BlocVotes> parseVotes(String json) {
    try {
      return mapper.readValue(json, new TypeReference<Map<String, BlocVotes>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Votes du candidat illisibles : " + json, e);
    }
  }
}

package fr.jrec.meteox.laws.opendata.senat;

import fr.jrec.meteox.laws.opendata.OpenDataDossiers;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Implémentation du pont uid ↔ slug fondée sur l'open data « dossiers législatifs » de l'AN
 * (réutilise {@link OpenDataDossiers}). Pour une législature, construit une fois une table
 * bidirectionnelle {@code uid ↔ titreChemin} à partir des dossiers AN, puis rend l'autre forme
 * d'une référence. Best-effort : toute erreur (open data AN indisponible, dossier absent) est
 * loggée et donne un ensemble vide — la loi restera simplement « non résolue » côté Sénat.
 */
@ApplicationScoped
public class OpenDataAnDossierAliases implements AnDossierAliases {

  private static final Logger LOG = Logger.getLogger(OpenDataAnDossierAliases.class);

  @Inject OpenDataDossiers dossiers;

  /** législature → (référence normalisée → forme alternative normalisée). Vide si indisponible. */
  private final Map<Integer, Map<String, String>> byLegislature = new ConcurrentHashMap<>();

  @Override
  public Set<String> aliasesFor(int legislature, String normalizedRef) {
    if (legislature <= 0 || normalizedRef == null || normalizedRef.isBlank()) {
      return Set.of();
    }
    String alt = index(legislature).get(normalizedRef);
    return alt == null ? Set.of() : Set.of(alt);
  }

  private Map<String, String> index(int legislature) {
    return byLegislature.computeIfAbsent(legislature, this::buildIndex);
  }

  private Map<String, String> buildIndex(int legislature) {
    Map<String, String> alias = new HashMap<>();
    try {
      dossiers.forEachDossier(
          legislature,
          d -> {
            String uid = DoslegDataset.normalizeAnRef(d.uid());
            String chemin =
                d.titreChemin() == null ? null : DoslegDataset.normalizeAnRef(d.titreChemin());
            if (!uid.isBlank() && chemin != null && !chemin.isBlank()) {
              alias.put(uid, chemin);
              alias.put(chemin, uid);
            }
          });
      LOG.infof(
          "Pont uid↔slug AN construit pour la %de législature (%d alias)", legislature, alias.size());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warnf("Construction du pont uid↔slug AN interrompue (lég. %d)", legislature);
    } catch (Exception e) {
      LOG.warnf(
          "Pont uid↔slug AN indisponible (lég. %d, %s) — appariement direct seul", legislature,
          e.getMessage());
    }
    return alias; // mémoïsé même vide : pas de nouvelle tentative réseau dans la même passe
  }
}

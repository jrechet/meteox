package fr.jrec.meteox.laws.opendata.acteurs;

import fr.jrec.meteox.laws.opendata.BlocMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Référentiel des acteurs (députés) de l'AN : résout un {@code acteurRef} (PA…) vers son nom et
 * son groupe politique actif. Construit paresseusement au premier appel à partir du jeu AMO30
 * (acteurs + organes) et du mapping {@code reference/organe-blocs.json} (via {@link BlocMapping}),
 * puis mémoïsé et rafraîchi périodiquement. Le sigle vient de l'organe ({@code libelleAbrev}), le
 * bloc du mapping — {@code null} si l'organeRef n'y figure pas (ne fait jamais échouer la résolution).
 */
@ApplicationScoped
public class ActeurReferentiel {

  private static final Logger LOG = Logger.getLogger(ActeurReferentiel.class);

  @Inject OpenDataActeurs openDataActeurs;
  @Inject BlocMapping blocMapping;

  @ConfigProperty(name = "meteox.acteurs.legislature", defaultValue = "17")
  int legislature;

  /** Fraîcheur de l'index mémoïsé : au-delà, il est reconstruit au prochain appel. */
  @ConfigProperty(name = "meteox.acteurs.refresh-seconds", defaultValue = "3600")
  long refreshSeconds;

  private volatile Index index;

  /** Rattachement d'un acteur à un groupe politique. {@code bloc} nul si l'organe n'est pas mappé. */
  public record GroupeAffiliation(String sigle, String organeRef, String bloc) {}

  /** Index immuable : acteurRef → nom (« Prénom Nom ») et acteurRef → affiliation de groupe. */
  private record Index(
      Map<String, String> nomByActeur,
      Map<String, GroupeAffiliation> groupeByActeur,
      Instant builtAt) {}

  /** Groupe politique actif d'un acteur, ou vide s'il n'a pas de mandat GP actif (ou inconnu). */
  public Optional<GroupeAffiliation> groupeDe(String acteurRef) {
    return Optional.ofNullable(index().groupeByActeur().get(acteurRef));
  }

  /** Nom (« Prénom Nom ») d'un acteur connu, ou vide si l'acteurRef est inconnu du référentiel. */
  public Optional<String> nomDe(String acteurRef) {
    return Optional.ofNullable(index().nomByActeur().get(acteurRef));
  }

  private Index index() {
    Index current = index;
    if (isFresh(current)) {
      return current;
    }
    synchronized (this) {
      if (isFresh(index)) {
        return index;
      }
      try {
        index = build();
      } catch (RuntimeException e) {
        if (index != null) {
          // Reconstruction impossible (open data indisponible) : on garde l'index précédent.
          LOG.warnf("Rafraîchissement du référentiel acteurs impossible (%s) — index conservé", e.getMessage());
          return index;
        }
        throw e;
      }
      return index;
    }
  }

  private boolean isFresh(Index candidate) {
    return candidate != null && candidate.builtAt().plusSeconds(refreshSeconds).isAfter(Instant.now());
  }

  private Index build() {
    Map<String, String> nomByActeur = new HashMap<>();
    Map<String, String> groupeRefByActeur = new HashMap<>();
    Map<String, String> sigleByOrgane = new HashMap<>();
    try {
      openDataActeurs.forEachEntry(
          legislature,
          a -> {
            nomByActeur.put(a.uid(), (a.prenom() + " " + a.nom()).trim());
            if (a.groupeOrganeRef() != null) {
              groupeRefByActeur.put(a.uid(), a.groupeOrganeRef());
            }
          },
          o -> {
            if (o.isGroupePolitique()) {
              sigleByOrgane.put(o.uid(), o.sigle());
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException("Référentiel acteurs indisponible : " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interruption pendant le chargement du référentiel acteurs", e);
    }

    Map<String, GroupeAffiliation> groupeByActeur = new HashMap<>();
    for (Map.Entry<String, String> e : groupeRefByActeur.entrySet()) {
      String organeRef = e.getValue();
      String sigle = sigleByOrgane.get(organeRef);
      String bloc = blocMapping.blocFor(organeRef).orElse(null);
      groupeByActeur.put(e.getKey(), new GroupeAffiliation(sigle, organeRef, bloc));
    }
    LOG.infof(
        "Référentiel acteurs construit : %d acteurs, %d avec groupe actif, %d groupes",
        nomByActeur.size(), groupeByActeur.size(), sigleByOrgane.size());
    return new Index(Map.copyOf(nomByActeur), Map.copyOf(groupeByActeur), Instant.now());
  }
}

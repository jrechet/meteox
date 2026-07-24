package fr.jrec.meteox.laws.opendata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.Signataire;
import fr.jrec.meteox.laws.opendata.SupportNetworkRepository.BlocSupport;
import fr.jrec.meteox.laws.opendata.SupportNetworkRepository.CrossGroupPair;
import fr.jrec.meteox.laws.opendata.SupportNetworkRepository.GroupLink;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Analyse réseau sur un scénario contrôlé de 3 dossiers : matrice de soutien par bloc, liens
 * entre groupes (seuil de récurrence), ponts transpartisans (auteur et cosignataire de groupes
 * différents, récurrents), et exclusion systématique des signataires non résolus.
 */
@QuarkusTest
class SupportNetworkRepositoryTest {

  private static final String D1 = "DLR5L17N97001";
  private static final String D2 = "DLR5L17N97002";
  private static final String D3 = "DLR5L17N97003";

  @Inject SupportNetworkRepository network;
  @Inject DossierSignataireRepository signataires;
  @Inject DataSource dataSource;

  @BeforeEach
  void seed() {
    // D1 : auteure LFI (gauche) — cosigné LFI ×2, SOC ×1, HOR ×1 (milieu), 1 non résolu.
    signataires.replaceForDossier(
        D1,
        List.of(
            new Signataire("auteur", "PA900", "Léa Rivière", "LFI-NFP", "gauche"),
            new Signataire("cosignataire", "PA901", "Ana Bard", "LFI-NFP", "gauche"),
            new Signataire("cosignataire", "PA902", "Bob Cyr", "LFI-NFP", "gauche"),
            new Signataire("cosignataire", "PA903", "Carl Diaz", "SOC", "gauche"),
            new Signataire("cosignataire", "PA904", "Dan Ève", "HOR", "milieu"),
            new Signataire("cosignataire", "PA905", null, null, null))); // exclu partout
    // D2 : même auteure LFI — recosigné par le même HOR (pont récurrent) + SOC.
    signataires.replaceForDossier(
        D2,
        List.of(
            new Signataire("auteur", "PA900", "Léa Rivière", "LFI-NFP", "gauche"),
            new Signataire("cosignataire", "PA904", "Dan Ève", "HOR", "milieu"),
            new Signataire("cosignataire", "PA903", "Carl Diaz", "SOC", "gauche")));
    // D3 : auteur DR (droite) — cosigné DR uniquement (aucun lien transpartisan).
    signataires.replaceForDossier(
        D3,
        List.of(
            new Signataire("auteur", "PA910", "Gil Roux", "DR", "droite"),
            new Signataire("cosignataire", "PA911", "Hedi Sy", "DR", "droite")));
  }

  @AfterEach
  void cleanup() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM dossier_signataires WHERE dossier_uid LIKE 'DLR5L17N97%'");
    }
  }

  @Test
  void matrice_de_soutien_par_bloc_compte_les_cosignatures_et_exclut_les_non_resolus() {
    List<BlocSupport> m = network.blocSupportMatrix();
    // Textes portés par la gauche : 4 cosignatures gauche (2 LFI + 2×SOC… D1: LFI×2+SOC ; D2: SOC) et 2 milieu (HOR ×2).
    assertEquals(
        4, m.stream().filter(r -> r.auteurBloc().equals("gauche") && r.cosignataireBloc().equals("gauche")).mapToInt(BlocSupport::cosignatures).sum());
    assertEquals(
        2, m.stream().filter(r -> r.auteurBloc().equals("gauche") && r.cosignataireBloc().equals("milieu")).mapToInt(BlocSupport::cosignatures).sum());
    // Textes portés par la droite : 1 cosignature droite, rien d'autre.
    assertEquals(
        1, m.stream().filter(r -> r.auteurBloc().equals("droite")).mapToInt(BlocSupport::cosignatures).sum());
    // Le cosignataire non résolu (bloc NULL) n'apparaît nulle part.
    assertTrue(m.stream().noneMatch(r -> r.cosignataireBloc() == null || r.auteurBloc() == null));
  }

  @Test
  void liens_entre_groupes_au_seuil_de_recurrence() {
    List<GroupLink> liens = network.groupLinks(2);
    // LFI↔SOC et LFI↔HOR co-présents sur D1 ET D2 → retenus ; DR n'est co-présent avec personne.
    assertTrue(liens.stream().anyMatch(l -> l.a().equals("HOR") && l.b().equals("LFI-NFP") && l.dossiers() == 2));
    assertTrue(liens.stream().anyMatch(l -> l.a().equals("LFI-NFP") && l.b().equals("SOC") && l.dossiers() == 2));
    assertFalse(liens.stream().anyMatch(l -> l.a().equals("DR") || l.b().equals("DR")));
    // Au seuil 3, plus rien.
    assertTrue(network.groupLinks(3).isEmpty());
  }

  @Test
  void ponts_transpartisans_recurrents_uniquement_entre_groupes_differents() {
    List<CrossGroupPair> ponts = network.crossGroupPairs(2, 10);
    // Deux ponts récurrents vers les textes de Léa Rivière (LFI) : Dan Ève (HOR, autre bloc)
    // et Carl Diaz (SOC — groupe différent, même bloc : transpartisan quand même).
    assertEquals(2, ponts.size());
    assertTrue(
        ponts.stream()
            .anyMatch(
                p ->
                    p.cosignataireNom().equals("Dan Ève")
                        && p.cosignataireSigle().equals("HOR")
                        && p.auteurNom().equals("Léa Rivière")
                        && p.dossiers() == 2));
    assertTrue(
        ponts.stream()
            .anyMatch(p -> p.cosignataireNom().equals("Carl Diaz") && p.dossiers() == 2));
    // Les cosignataires du MÊME groupe que l'auteur (Ana, Bob — LFI) ne sont jamais des ponts,
    // et le duo intra-DR (D3) non plus.
    assertTrue(ponts.stream().noneMatch(p -> p.cosignataireSigle().equals(p.auteurSigle())));
    assertTrue(ponts.stream().noneMatch(p -> p.auteurNom().equals("Gil Roux")));
  }
}

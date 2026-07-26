package fr.jrec.meteox.laws.opendata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jrec.meteox.laws.opendata.DossierSignataireRepository.Signataire;
import fr.jrec.meteox.laws.opendata.SupportNetworkRepository.Coverage;
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
 * Mesure du taux de résolution (DoD issue #58 : « mesurer le taux avant/après »). Sur un scénario
 * contrôlé, {@link SupportNetworkRepository#coverage()} compte les dossiers dotés d'un auteur, la
 * part de signataires au groupe résolu, et repère les sigles présents mais SANS bloc (trous du
 * mapping {@code organe-blocs.json}).
 */
@QuarkusTest
class SupportNetworkCoverageTest {

  private static final String C1 = "DLR5L17N98001";
  private static final String C2 = "DLR5L17N98002";
  private static final String C3 = "DLR5L17N98003";

  @Inject SupportNetworkRepository network;
  @Inject DossierSignataireRepository signataires;
  @Inject DataSource dataSource;

  @BeforeEach
  void seed() {
    // C1 : auteur résolu + 2 cosignataires résolus + 1 cosignataire au groupe inconnu (« ? »).
    signataires.replaceForDossier(
        C1,
        List.of(
            new Signataire("auteur", "PA800", "Léa Rivière", "LFI-NFP", "gauche"),
            new Signataire("cosignataire", "PA801", "Ana Bard", "LFI-NFP", "gauche"),
            new Signataire("cosignataire", "PA802", "Bob Cyr", "SOC", "gauche"),
            new Signataire("cosignataire", "PA803", "Cid Dax", null, null))); // groupe non résolu
    // C2 : auteur PERSONNE dont le groupe n'a pas été résolu (mandat actif manquant → « ? »).
    signataires.replaceForDossier(
        C2,
        List.of(
            new Signataire("auteur", "PA810", "Gil Roux", null, null),
            new Signataire("cosignataire", "PA811", "Hoa Sy", "DR", "droite")));
    // C3 : groupe résolu (sigle) mais ABSENT du mapping organe-blocs → bloc null (trou de mapping).
    signataires.replaceForDossier(
        C3,
        List.of(
            new Signataire("auteur", "PA820", "Ivo Tal", "NI", null),
            new Signataire("cosignataire", "PA821", "Jo Urs", "NI", null)));
  }

  @AfterEach
  void cleanup() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement()) {
      st.executeUpdate("DELETE FROM dossier_signataires WHERE dossier_uid LIKE 'DLR5L17N98%'");
    }
  }

  @Test
  void couverture_compte_dossiers_auteurs_et_groupes_resolus() {
    Coverage cov = network.coverage();

    assertEquals(3, cov.dossiersAvecSignataires());
    assertEquals(3, cov.dossiersAvecAuteur());
    // 8 signataires au total : 3 auteurs + 5 cosignataires.
    assertEquals(8, cov.signataires());
    assertEquals(5, cov.cosignataires());
    // Groupe résolu (sigle non nul) : PA800, PA801, PA802, PA811, PA820, PA821 = 6/8.
    assertEquals(6, cov.signatairesAvecGroupe());
    // Cosignataires au groupe résolu : PA801, PA802, PA811, PA821 = 4/5 (PA803 est « ? »).
    assertEquals(4, cov.cosignatairesAvecGroupe());
  }

  @Test
  void couverture_repere_les_sigles_sans_bloc_du_mapping() {
    Coverage cov = network.coverage();
    // NI a un sigle mais aucun bloc dans le mapping → il doit remonter comme trou de mapping.
    assertTrue(cov.siglesSansBloc().contains("NI"));
    // Un sigle correctement mappé ne doit jamais apparaître comme « sans bloc ».
    assertTrue(cov.siglesSansBloc().stream().noneMatch(s -> s.equals("LFI-NFP") || s.equals("SOC")));
  }
}

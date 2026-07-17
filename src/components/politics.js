import { escapeHtml, safeUrl } from '../lib/html.js';

export const citizenActionIcon = `
  <svg class="citizen-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
    <path d="M2 6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6z" />
    <path d="m2 6 8 5.5L18 6" />
    <path d="M21 7h-3l2 4h-3l3 6.5" stroke="var(--color-accent)" stroke-width="2.5" />
  </svg>
`;

function categoryLabel(cat) {
  const labels = {
    pesticides: 'Pesticides 🧪',
    eau: 'Gestion de l\'Eau 💧',
    canicule: 'Canicule & Chaleur ☀️',
    agriculture: 'Agriculture & Sols 🚜'
  };
  return labels[cat] || cat;
}

function indicatorMeterHTML(name, val) {
  if (val == null) return '';
  
  // Map value (-2 to +2) to percentage (0% to 100%)
  const percentage = ((val + 2) / 4) * 100;
  
  let colorClass = 'meter-val--neutral';
  let desc = 'Aucun impact';
  
  if (val > 0.5) {
    colorClass = 'meter-val--positive';
    desc = val > 1.2 ? 'Très bénéfique' : 'Favorable';
  } else if (val < -0.5) {
    colorClass = 'meter-val--negative';
    desc = val < -1.2 ? 'Très néfaste / Risque' : 'Négatif';
  }

  const signedVal = val > 0 ? `+${val}` : `${val}`;
  return `
    <div class="indicator-meter">
      <span class="sr-only">${name} : ${desc}, ${signedVal} sur une échelle de −2 (très néfaste) à +2 (très bénéfique).</span>
      <div class="indicator-meter__label" aria-hidden="true">
        <span>${name}</span>
        <span class="indicator-meter__desc ${colorClass}">${desc}</span>
      </div>
      <div class="indicator-meter__track" aria-hidden="true">
        <div class="indicator-meter__bullet ${colorClass}" style="left: ${percentage}%"></div>
      </div>
    </div>
  `;
}

function voteGroupHTML(partyName, votes) {
  if (!votes) return '';
  const total = votes.for + votes.against + votes.abstained || 1;
  const pctFor = Math.round((votes.for / total) * 100);
  const pctAgainst = Math.round((votes.against / total) * 100);
  const pctAbstained = Math.round((votes.abstained / total) * 100);

  return `
    <div class="vote-group">
      <span class="sr-only">${partyName} : ${votes.for} pour, ${votes.against} contre, ${votes.abstained} abstention${votes.abstained > 1 ? 's' : ''}.</span>
      <span class="vote-group__name" aria-hidden="true" title="${partyName}">${partyName}</span>
      <div class="vote-group__bar" aria-hidden="true">
        <div class="vote-group__segment vote-group__segment--for" style="width: ${pctFor}%" title="Pour: ${pctFor}%"></div>
        <div class="vote-group__segment vote-group__segment--against" style="width: ${pctAgainst}%" title="Contre: ${pctAgainst}%"></div>
        <div class="vote-group__segment vote-group__segment--abstained" style="width: ${pctAbstained}%" title="Abstention: ${pctAbstained}%"></div>
      </div>
      <span class="vote-group__numbers" aria-hidden="true">
        <span class="vote-num vote-num--for">${pctFor}% P</span> / <span class="vote-num vote-num--against">${pctAgainst}% C</span>
      </span>
    </div>
  `;
}

const INDICATOR_LABELS = {
  pesticides: 'Pesticides',
  partageEau: "Partage de l'eau",
  pognonPuissants: 'Intérêts privés vs intérêt général',
  peupleSante: 'Santé & population',
};

const CONFIDENCE_LABELS = { haute: 'confiance haute', moyenne: 'confiance moyenne', basse: 'confiance basse' };

/**
 * Dépliant « Pourquoi ces scores ? » (issue #4) : justification citée, confiance et
 * provenance de chaque score publié + lien vers la méthodologie. Contenu chargé au
 * premier dépliage (data-law-id consommé par main.js).
 */
function indicatorsWhyHTML(lawId) {
  return `
    <details class="indicators-why" data-law-id="${escapeHtml(lawId)}">
      <summary class="indicators-why__summary">Pourquoi ces scores ?</summary>
      <div class="indicators-why__body" data-role="why-body" aria-live="polite">
        <p class="indicators-why__loading">Chargement de la justification…</p>
      </div>
    </details>
  `;
}

/** Rendu du détail des indicateurs (appelé par main.js une fois la réponse API reçue). */
export function indicatorsWhyBodyHTML(payload) {
  const methodologyLink = `<a href="${escapeHtml((payload && payload.methodology) || '')}" target="_blank" rel="noopener" class="pcard__link">Méthodologie complète ↗</a>`;
  if (!payload) {
    return `
      <p class="indicators-why__fallback">Justifications indisponibles pour le moment (source injoignable).
      Chaque score est documenté publiquement :</p>
      <a href="https://github.com/jrechet/meteox/blob/main/docs/methodologie-indicateurs.md" target="_blank" rel="noopener" class="pcard__link">Méthodologie complète ↗</a>
    `;
  }
  const rows = payload.indicators
    .map((row) => {
      const label = INDICATOR_LABELS[row.indicator] || row.indicator;
      const provenance = row.model
        ? `Extraction assistée (<code>${escapeHtml(row.model)}</code>), relue et validée${row.reviewedBy ? ` par ${escapeHtml(row.reviewedBy)}` : ''}`
        : 'Score éditorial vérifié (relecture humaine)';
      const confidence = row.confidence
        ? ` · <span class="indicators-why__confidence">${escapeHtml(CONFIDENCE_LABELS[row.confidence] || row.confidence)}</span>`
        : '';
      const justification = row.justification
        ? `<p class="indicators-why__text">${escapeHtml(row.justification)}</p>`
        : '';
      const citation = row.citation
        ? `<blockquote class="indicators-why__citation">« ${escapeHtml(row.citation)} »</blockquote>`
        : '';
      return `
        <div class="indicators-why__row">
          <p class="indicators-why__head"><strong>${escapeHtml(label)}</strong> · ${provenance}${confidence}</p>
          ${justification}
          ${citation}
        </div>
      `;
    })
    .join('');
  return `${rows}${methodologyLink}`;
}

/** Squelette dimensionné (pas de layout shift) affiché pendant le fetch API. */
function loadingSkeletonHTML() {
  const card = `
    <article class="pcard pcard--skeleton" aria-hidden="true">
      <div class="skel skel--badge"></div>
      <div class="skel skel--title"></div>
      <div class="skel skel--line"></div>
      <div class="skel skel--line skel--short"></div>
      <div class="skel skel--block"></div>
    </article>
  `;
  return `
    <div class="politics-tab" aria-busy="true">
      <p class="sr-only" role="status">Chargement des données législatives…</p>
      <section class="politics-section">
        <div class="politics-passed-grid">${card}${card}</div>
      </section>
    </div>
  `;
}

/** Indicateur discret de fraîcheur : provenance + date du jeu de données. */
function freshnessHTML(meta) {
  if (!meta) return '';
  const day = new Date(meta.dataDate).toLocaleDateString('fr-FR');
  const label =
    meta.source === 'api'
      ? `Données à jour du ${day}`
      : `Données archivées du ${day} — source temporairement injoignable`;
  return `<p class="politics-freshness" data-source="${meta.source}">${label}</p>`;
}

export function politicsHTML(state) {
  const laws = state.laws;
  if (!laws) return loadingSkeletonHTML();

  const activeFilter = state.lawFilter || 'all';

  const upcoming = laws.filter(l => l.status === 'upcoming');
  const passed = laws.filter(l => {
    if (l.status !== 'passed') return false;
    if (activeFilter === 'all') return true;
    return l.category === activeFilter;
  });

  const categories = ['all', 'pesticides', 'eau', 'canicule', 'agriculture'];
  const filterChips = categories.map(cat => {
    const active = activeFilter === cat;
    const label = cat === 'all' ? 'Toutes les catégories' : categoryLabel(cat);
    return `<button class="chip chip--sm ${active ? 'chip--active' : ''}" data-lawfilter="${cat}">${label}</button>`;
  }).join('');

  const upcomingCards = upcoming.map(law => `
    <article class="pcard pcard--upcoming">
      <div class="pcard__badge">Vote à venir · ${new Date(law.date).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' })}</div>
      <h4 class="pcard__title">${escapeHtml(law.title)}</h4>
      <p class="pcard__summary">${escapeHtml(law.summary)}</p>

      <div class="pcard__indicators-grid">
        ${indicatorMeterHTML('Impact pesticides', law.indicators.pesticides)}
        ${indicatorMeterHTML('Partage de l\'eau', law.indicators.partageEau)}
        ${indicatorMeterHTML('Intérêts privés vs intérêt général', law.indicators.pognonPuissants)}
        ${indicatorMeterHTML('Santé & population', law.indicators.peupleSante)}
      </div>

      <div class="pcard__actions">
        <a href="${escapeHtml(safeUrl(law.textUrl))}" target="_blank" rel="noopener" class="pcard__link">Voir le texte (.gouv) ↗</a>
        <button class="btn btn--citoyen btn--sm" data-action="interpellate" data-law-id="${escapeHtml(law.id)}">
          ${citizenActionIcon}
          <span>Interpeller mon député</span>
        </button>
      </div>
    </article>
  `).join('');

  const passedCards = passed.map(law => `
    <article class="pcard">
      <div class="pcard__meta">
        <span class="pcard__cat">${escapeHtml(categoryLabel(law.category))}</span>
        <span class="pcard__date">Voté le ${new Date(law.date).toLocaleDateString('fr-FR')}</span>
      </div>
      <h4 class="pcard__title">${escapeHtml(law.title)}</h4>
      <p class="pcard__summary">${escapeHtml(law.summary)}</p>
      
      <div class="pcard__body-layout">
        <div class="pcard__section">
          <h5 class="pcard__section-title">Indicateurs d'Impact</h5>
          ${indicatorMeterHTML('Pesticides', law.indicators.pesticides)}
          ${indicatorMeterHTML('Partage de l\'eau', law.indicators.partageEau)}
          ${indicatorMeterHTML('Intérêts privés vs intérêt général', law.indicators.pognonPuissants)}
          ${indicatorMeterHTML('Santé & population', law.indicators.peupleSante)}
          ${indicatorsWhyHTML(law.id)}
        </div>
        
        <div class="pcard__section">
          <h5 class="pcard__section-title">Vote des Groupes (Pour / Contre)</h5>
          <div class="vote-legend">
            <span class="vote-legend__item"><span class="vote-legend__dot vote-legend__dot--for"></span> Pour (P)</span>
            <span class="vote-legend__item"><span class="vote-legend__dot vote-legend__dot--against"></span> Contre (C)</span>
            <span class="vote-legend__item"><span class="vote-legend__dot vote-legend__dot--abstained"></span> Abstention</span>
          </div>
          ${voteGroupHTML('Gauche (NFP/LFI/PS/EELV)', law.votes?.gauche)}
          ${voteGroupHTML('Centre (EPR/MoDem/Horizons)', law.votes?.milieu)}
          ${voteGroupHTML('Droite (DR/LR)', law.votes?.droite)}
          ${voteGroupHTML('Extrême Droite (RN/UDR)', law.votes?.extremeDroite)}
        </div>
      </div>

      <div class="pcard__actions">
        <a href="${escapeHtml(safeUrl(law.textUrl))}" target="_blank" rel="noopener" class="pcard__link">Voir le texte (.gouv) ↗</a>
        <a href="${escapeHtml(safeUrl(law.sourceUrl))}" target="_blank" rel="noopener" class="pcard__link">Scrutin officiel ↗</a>
      </div>
    </article>
  `).join('');

  return `
    <div class="politics-tab">
      ${freshnessHTML(state.lawsMeta)}
      <section class="politics-section">
        <div class="politics-section__header">
          <h3 class="politics-section__title">📜 Mobilisation & Prochains Scrutins</h3>
          <p class="politics-section__desc">Les projets législatifs cruciaux en cours de discussion. Agissez avant le vote pour faire pression sur vos représentants locaux.</p>
        </div>
        <div class="politics-upcoming-grid">
          ${upcomingCards.length > 0 ? upcomingCards : '<p class="empty-state">Aucun scrutin vérifié à venir pour le moment. Seuls les textes dont le dossier officiel est confirmé sont affichés ici.</p>'}
        </div>
      </section>

      <section class="politics-section">
        <div class="politics-section__header">
          <h3 class="politics-section__title">⚖️ Bilan des Lois Votées</h3>
          <p class="politics-section__desc">L'impact réel des récents textes législatifs et le positionnement des forces politiques nationales.</p>
        </div>
        
        <div class="chips-row">
          ${filterChips}
        </div>

        <div class="politics-passed-grid">
          ${passedCards.length > 0 ? passedCards : '<p class="empty-state">Aucun texte voté dans cette catégorie.</p>'}
        </div>
      </section>
    </div>
  `;
}

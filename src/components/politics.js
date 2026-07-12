import { LAWS_DATA } from '../lib/laws.js';

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

  return `
    <div class="indicator-meter">
      <div class="indicator-meter__label">
        <span>${name}</span>
        <span class="indicator-meter__desc ${colorClass}">${desc}</span>
      </div>
      <div class="indicator-meter__track">
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
      <span class="vote-group__name">${partyName}</span>
      <div class="vote-group__bar">
        <div class="vote-group__segment vote-group__segment--for" style="width: ${pctFor}%" title="Pour: ${pctFor}%"></div>
        <div class="vote-group__segment vote-group__segment--against" style="width: ${pctAgainst}%" title="Contre: ${pctAgainst}%"></div>
        <div class="vote-group__segment vote-group__segment--abstained" style="width: ${pctAbstained}%" title="Abstention: ${pctAbstained}%"></div>
      </div>
      <span class="vote-group__numbers">${pctFor}% / ${pctAgainst}%</span>
    </div>
  `;
}

export function politicsHTML(state) {
  const activeFilter = state.lawFilter || 'all';
  
  const upcoming = LAWS_DATA.filter(l => l.status === 'upcoming');
  const passed = LAWS_DATA.filter(l => {
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
      <h4 class="pcard__title">${law.title}</h4>
      <p class="pcard__summary">${law.summary}</p>
      
      <div class="pcard__indicators-grid">
        ${indicatorMeterHTML('Impact pesticides', law.indicators.pesticides)}
        ${indicatorMeterHTML('Partage de l\'eau', law.indicators.partageEau)}
        ${indicatorMeterHTML('Lobbies vs Citoyens', law.indicators.pognonPuissants)}
        ${indicatorMeterHTML('Peuple & Santé', law.indicators.peupleSante)}
      </div>

      <div class="pcard__actions">
        <a href="${law.sourceUrl}" target="_blank" rel="noopener" class="pcard__link">Voir le texte (.gouv) ↗</a>
        <button class="btn btn--citoyen btn--sm" data-action="interpellate" data-law-id="${law.id}">
          ${citizenActionIcon}
          <span>Interpeller mon député</span>
        </button>
      </div>
    </article>
  `).join('');

  const passedCards = passed.map(law => `
    <article class="pcard">
      <div class="pcard__meta">
        <span class="pcard__cat">${categoryLabel(law.category)}</span>
        <span class="pcard__date">Voté le ${new Date(law.date).toLocaleDateString('fr-FR')}</span>
      </div>
      <h4 class="pcard__title">${law.title}</h4>
      <p class="pcard__summary">${law.summary}</p>
      
      <div class="pcard__body-layout">
        <div class="pcard__section">
          <h5 class="pcard__section-title">Indicateurs d'Impact</h5>
          ${indicatorMeterHTML('Pesticides', law.indicators.pesticides)}
          ${indicatorMeterHTML('Partage de l\'eau', law.indicators.partageEau)}
          ${indicatorMeterHTML('Monopolisation vs Citoyens', law.indicators.pognonPuissants)}
          ${indicatorMeterHTML('Santé & Population', law.indicators.peupleSante)}
        </div>
        
        <div class="pcard__section">
          <h5 class="pcard__section-title">Vote des Groupes (Pour / Contre)</h5>
          ${voteGroupHTML('Gauche (NFP/LFI/PS/EELV)', law.votes.gauche)}
          ${voteGroupHTML('Milieu (EPR/MoDem/Horizon)', law.votes.milieu)}
          ${voteGroupHTML('Droite (DR/LR)', law.votes.droite)}
          ${voteGroupHTML('Extrême Droite (RN)', law.votes.extremeDroite)}
        </div>
      </div>

      <div class="pcard__actions">
        <a href="${law.sourceUrl}" target="_blank" rel="noopener" class="pcard__link">Scrutin officiel ↗</a>
      </div>
    </article>
  `).join('');

  return `
    <div class="politics-tab">
      <section class="politics-section">
        <div class="politics-section__header">
          <h3 class="politics-section__title">📜 Mobilisation & Prochains Scrutins</h3>
          <p class="politics-section__desc">Les projets législatifs cruciaux en cours de discussion. Agissez avant le vote pour faire pression sur vos représentants locaux.</p>
        </div>
        <div class="politics-upcoming-grid">
          ${upcomingCards}
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

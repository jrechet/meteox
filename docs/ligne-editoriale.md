# Ligne éditoriale — onglet « Lois & Climat »

> Statut : **proposition** (issue #6) — la validation finale du mainteneur se fait sur la PR.
> Une fois validée, toute nouvelle formulation UI doit s'y conformer.

## 1. Posture

- **Engagée sur les finalités, factuelle sur les données.** Le site assume de juger les textes
  à l'aune de l'intérêt général (climat, santé, partage de l'eau). En revanche, tout chiffre,
  date, statut ou vote affiché est **strictement factuel et traçable** vers une source officielle
  (Golden Rules d'`AGENTS.md`) : l'opinion porte sur l'interprétation, jamais sur les faits.
- **Séparation visible opinion / fait.** Les indicateurs −2..+2 sont une lecture éditoriale et
  seront accompagnés de leur justification citée (issue #4). Les votes et liens sont des faits.
- **Pas de dérision nominative.** On qualifie des textes et des mécanismes (lobbying,
  concentration des ressources), pas des personnes.

## 2. Vocabulaire des libellés UI

| Ancien libellé | Libellé retenu | Raison |
|---|---|---|
| « Monopolisation vs Citoyens » / « Lobbies vs Citoyens » (deux variantes pour le même axe) | **« Intérêts privés vs intérêt général »** | Une seule formulation pour le même indicateur ; garde le point de vue assumé, sans familiarité (« pognon ») ni imprécision (« monopolisation ») |
| « Peuple & Santé » / « Santé & Population » (deux variantes) | **« Santé & population »** | Harmonisation ; « peuple » a une charge politicienne inutile ici |
| « Milieu (EPR/MoDem/Horizon) » | **« Centre (EPR/MoDem/Horizons) »** | « Centre » est le terme politologique standard ; « Horizons » avec s |
| « Pesticides », « Partage de l'eau » | inchangés | Déjà factuels |
| « Extrême Droite (RN/UDR) », « Gauche (NFP/LFI/PS/EELV) », « Droite (DR/LR) » | inchangés | Classification politologique courante ; le détail des groupes agrégés reste affiché |

Le nom de clé du modèle (`pognonPuissants`, etc.) est un identifiant technique interne : il ne
change pas tant que le modèle de données n'est pas migré (backend, issue #2).

## 3. Règles d'écriture

1. Toute affirmation chiffrée est adossée à un lien officiel visible à proximité.
2. Les résumés de lois décrivent d'abord **ce que fait le texte**, ensuite son effet estimé.
3. Les formulations de jugement utilisent le vocabulaire des indicateurs (favorable / négatif /
   très néfaste…) — pas d'hyperbole hors échelle.
4. Le courrier d'interpellation reste en première personne citoyenne, respectueux et ferme
   (« Je vous demande solennellement… ») ; pas d'injonction agressive.
5. Les états vides disent la vérité du système (« Aucun scrutin vérifié à venir ») plutôt
   qu'un vague « bientôt disponible ».

## 4. Ce que cette ligne interdit

- Publier un chiffre de vote, une date ou un statut non vérifiés (cf. Golden Rules).
- Requalifier un indicateur sans justification citée archivée.
- Employer des libellés moqueurs ou familiers dans l'UI (« pognon », « magouilles »…) —
  le ton engagé passe par le choix des axes, pas par le registre familier.

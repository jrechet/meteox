# Méthodologie des indicateurs — onglet « Lois & Climat »

> Répond à la question ouverte « Méthodo des indicateurs −2..+2 » du PRD (issue #4).
> Conforme à la [ligne éditoriale](ligne-editoriale.md) : engagée sur les finalités, factuelle sur les données.

## 1. Ce que mesure un indicateur

Chaque loi est notée sur 4 axes, de **−2** (très néfaste pour l'intérêt général) à **+2**
(très favorable), par pas de 0,5 :

| Clé technique | Libellé UI | Ce qui est évalué |
|---|---|---|
| `pesticides` | Pesticides | Réduction de l'exposition aux pesticides et polluants |
| `partageEau` | Partage de l'eau | Partage équitable et préservation de la ressource en eau |
| `pognonPuissants` | Intérêts privés vs intérêt général | Un score négatif signifie que le texte favorise des intérêts privés concentrés au détriment de l'intérêt général |
| `peupleSante` | Santé & population | Effets sur la santé publique |

Un score est **une lecture éditoriale assumée**, jamais un fait : c'est la nuance que la ligne
éditoriale demande de garder visible pour l'utilisateur (cf. § 1 de `ligne-editoriale.md`).

## 2. Comment un score est produit

1. **Entrée** : le texte source de la loi (exposé des motifs ou résumé du dossier législatif
   officiel — jamais un texte reformulé ou résumé par un tiers non vérifié).
2. **Extraction assistée par IA** : un modèle de langage lit ce texte et produit, pour chaque
   axe, un score, une **justification** (1 à 3 phrases) et une **citation** — un extrait exact,
   copié mot pour mot du texte source — plus un niveau de **confiance** (faible / moyenne / haute).
   Le modèle n'a accès à rien d'autre que le texte fourni : il n'invente pas de faits externes.
3. **Rejet automatique** : un score hors de l'intervalle [−2, 2], ou une citation qui ne peut
   pas être retrouvée verbatim dans le texte source, est rejeté avant même d'atteindre la base
   de données (aucune hallucination publiable).
4. **Statut `draft`** : le score créé n'est jamais visible publiquement à ce stade — ni dans
   `GET /api/laws`, ni dans `GET /api/laws/{id}/indicators`.
5. **Relecture humaine obligatoire** : un score `draft` n'est publié qu'après validation
   explicite par une personne (`reviewedBy` + horodatage `reviewedAt`). Rien ne passe en
   production sans ce geste humain — l'IA propose, une personne dispose.
6. **Publication** : une fois validé, le score apparaît côté public avec sa justification, sa
   citation et son niveau de confiance, consultables via `GET /api/laws/{id}/indicators`.
7. **Piste d'audit** : chaque étape (création du draft, publication) est journalisée — qui a
   fait quoi, sur quel score, quand — et consultable via l'API admin.

## 3. Backends d'extraction

L'extraction est **interchangeable par configuration** (`quarkus.laws.ai.backend`), sans
changer une ligne de code :

- **`claude-cli`** (défaut) — `claude -p` en local/serveur, réutilise l'abonnement Claude
  existant, aucun coût API supplémentaire.
- **`claude-api`** — API Anthropic (modèle `claude-sonnet-5`), clé fournie uniquement via
  secret d'environnement (`MX_ANTHROPIC_API_KEY`), utilisable en environnement cloud/CI.
- **`ollama`** — modèle local (ex. `llama3.1`) via une instance Ollama, gratuit mais qualité
  moindre attendue sur du texte législatif français ; réservé au développement.

Les trois backends produisent le même contrat de sortie (score / justification / citation /
confiance) et passent le même test de conformité — changer de backend n'affecte jamais le
format des données publiées.

## 4. Ce que cette méthodologie garantit

- Aucun score n'apparaît sans citation vérifiable dans le texte source.
- Aucun score n'est publié sans relecture humaine explicite.
- Le raisonnement (justification + citation + confiance) est public, pas seulement le chiffre
  final — c'est la différence entre « un score » et « un score qu'on peut vérifier ».
- Le modèle d'origine (`claude-cli`, `claude-api:claude-sonnet-5`, `ollama:llama3.1`, ou `null`
  pour une notation éditoriale antérieure au module IA) reste attaché au score publié.

## 5. Limites connues

- La qualité d'une citation dépend de la qualité et de l'exhaustivité du texte source fourni ;
  un exposé des motifs laconique produit des scores à confiance « faible ».
- Le score reste un jugement éditorial sur un texte juridique complexe : il ne remplace pas la
  lecture du texte intégral, dont le lien officiel est toujours affiché à côté de chaque score.

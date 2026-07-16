-- Issue #3 — correction des votes 16e sur la donnée officielle (open data AN).
-- Les chiffres du seed V2 (hérités de l'ancien plan navigation-lois-fiabilisation.md,
-- jamais revérifiés page par page) ne concordaient PAS avec le décompte officiel des
-- scrutins. Valeurs ci-dessous = agrégation par bloc de l'open data (jeux Scrutins 16e),
-- validée par la page officielle de chaque scrutin. Cf. ScrutinExtractionTest.
-- V3 était réservée à cette issue (V4 = issue #4).

-- eco-agri-1 — PFAS, scrutin n°3643
UPDATE scrutins SET votes_for=98, votes_against=0,  votes_abstained=0  WHERE law_id='eco-agri-1' AND bloc='gauche';
UPDATE scrutins SET votes_for=86, votes_against=0,  votes_abstained=0  WHERE law_id='eco-agri-1' AND bloc='milieu';
UPDATE scrutins SET votes_for=0,  votes_against=0,  votes_abstained=4  WHERE law_id='eco-agri-1' AND bloc='droite';
UPDATE scrutins SET votes_for=0,  votes_against=0,  votes_abstained=22 WHERE law_id='eco-agri-1' AND bloc='extremeDroite';

-- eco-eau-1 — APER, scrutin n°823
UPDATE scrutins SET votes_for=28,  votes_against=91, votes_abstained=24 WHERE law_id='eco-eau-1' AND bloc='gauche';
UPDATE scrutins SET votes_for=257, votes_against=2,  votes_abstained=6  WHERE law_id='eco-eau-1' AND bloc='milieu';
UPDATE scrutins SET votes_for=1,   votes_against=56, votes_abstained=4  WHERE law_id='eco-eau-1' AND bloc='droite';
UPDATE scrutins SET votes_for=0,   votes_against=87, votes_abstained=0  WHERE law_id='eco-eau-1' AND bloc='extremeDroite';

-- eco-canicule-1 — Industrie verte, scrutin n°2721
UPDATE scrutins SET votes_for=0,   votes_against=61, votes_abstained=16 WHERE law_id='eco-canicule-1' AND bloc='gauche';
UPDATE scrutins SET votes_for=154, votes_against=0,  votes_abstained=2  WHERE law_id='eco-canicule-1' AND bloc='milieu';
UPDATE scrutins SET votes_for=29,  votes_against=1,  votes_abstained=1  WHERE law_id='eco-canicule-1' AND bloc='droite';
UPDATE scrutins SET votes_for=47,  votes_against=0,  votes_abstained=0  WHERE law_id='eco-canicule-1' AND bloc='extremeDroite';

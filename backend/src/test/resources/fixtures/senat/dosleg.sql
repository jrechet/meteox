-- Fixture Dosleg minimale (extraite du dump réel data.senat.fr le 2026-07-21).
-- Chaînes APER (loicod 70063, 2 scrutins « sur l'ensemble ») et PFAS (loicod 73906,
-- amendements uniquement, aucun « sur l'ensemble »). En-têtes COPY réduits aux colonnes utiles.

COPY loi (loicod, signet, url_an) FROM stdin;
70063	pjl21-889	http://www.assemblee-nationale.fr/16/dossiers/DLR5L16N46539.asp
73906	ppl23-514	http://www.assemblee-nationale.fr/16/dossiers/proteger_population_risques_pfas.asp
\.

COPY lecture (lecidt, loicod, typleccod, leccom) FROM stdin;
83630	70063	4	Commission mixte paritaire (accord)
82750	70063	1	Première lecture
86973	73906	1	Première lecture
\.

COPY lecass (lecassidt, lecidt, codass) FROM stdin;
94651	83630	I
93725	82750	S
97631	86973	S
\.

COPY date_seance (lecidt, code) FROM stdin;
94651	27519
93725	27120
97631	28692
\.

COPY scr (sesann, scrnum, code, scrint, scrdat) FROM stdin;
2022	125	27519	sur l'ensemble du texte élaboré par la commission mixte paritaire sur le projet de loi relatif à l'accélération de la production d'énergies renouvelables	2023-02-07 00:00:00
2022	20	27120	sur l'amendement n° 576 rectifié bis, présenté par M. Patrick Chauvet et plusieurs de ses collègues, à l'article 17 du projet de loi relatif à l'accélération de la production d'énergies renouvelables (procédure accélérée)	2022-11-04 00:00:00
2022	22	27120	sur les amendements identiques n° 46 rectifié ter, présenté par M. Stéphane Sautarel et plusieurs de ses collègues et n° 446, présenté par M. Fabien Gay et les membres du groupe communiste républicain citoyen et écologiste, tendant à supprimer l'article 18 du projet de loi relatif à l'accélération de la production d'énergies renouvelables (procédure accélérée)	2022-11-04 00:00:00
2022	26	27120	sur le sous-amendement n° 677, présenté par le Gouvernement, à l'amendement n° 12 rectifié bis, à l'article 19 bis du projet de loi relatif à l'accélération de la production d'énergies renouvelables (procédure accélérée)	2022-11-04 00:00:00
2022	29	27120	sur l'ensemble du projet de loi relatif à l'accélération de la production d'énergies renouvelables (procédure accélérée)	2022-11-04 00:00:00
2023	208	28692	sur lamendement n° 14, présenté par Mme Anne Souyris et les membres du groupe Écologiste - Solidarité, et Territoires, à larticle 1er de la proposition de loi visant à protéger la population des risques liés aux substances perfluoroalkylées et polyfluoroalkylées	2024-05-30 00:00:00
2023	206	28692	sur lamendement n° 4, présenté par M. Hervé Gillé et les membres du groupe Socialiste, Écologiste et Républicain, à larticle 1er de la proposition de loi visant à protéger la population des risques liés aux substances perfluoroalkylées et polyfluoroalkylées	2024-05-30 00:00:00
2023	207	28692	sur lamendement n° 25 rectifié, présenté par M. Didier Rambaud et les membres du groupe Rassemblement des démocrates, progressistes et indépendants, à larticle 1er de la proposition de loi visant à protéger la population des risques liés aux substances perfluoroalkylées et polyfluoroalkylées	2024-05-30 00:00:00
\.


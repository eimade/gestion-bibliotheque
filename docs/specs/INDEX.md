---
feature: index
agent: spec-kit-ba
version: 1
statut: READY
sources: [besoin utilisateur (conversation du 2026-10-17), .claude/agents/1-spec-kit-ba.md]
---
# Index des spécifications — Application de gestion de bibliothèque

## 1. Vision produit
Permettre à une bibliothèque de prêt (un seul site) de gérer de bout en bout son fonds documentaire et la circulation des documents : catalogue public, gestion des exemplaires, inscription des adhérents, emprunts, retours, prolongations, réservations et pénalités, avec un espace personnel pour chaque adhérent et des indicateurs de pilotage pour l'administration.

**Indicateurs de succès globaux**
- 100 % des emprunts et retours enregistrés dans l'application (fin des registres papier) dès la mise en service.
- Enregistrement d'un emprunt ou d'un retour au guichet en moins de 30 secondes (mesuré du début de la saisie à la confirmation).
- Taux d'emprunts rendus en retard en baisse de 20 % six mois après la mise en service, grâce à la visibilité des échéances dans l'espace adhérent.

## 2. Découpage en features (ordre de réalisation recommandé)
| Ordre | Slug | Spec | Objet | Dépend de |
|---|---|---|---|---|
| 1 | `authentification` | [SPEC-authentification.md](SPEC-authentification.md) | Comptes, connexion, rôles | — |
| 2 | `catalogue` | [SPEC-catalogue.md](SPEC-catalogue.md) | Notices, auteurs, catégories, exemplaires, recherche | authentification |
| 3 | `adherents` | [SPEC-adherents.md](SPEC-adherents.md) | Inscription, adhésion, espace adhérent, RGPD | authentification |
| 4 | `emprunts` | [SPEC-emprunts.md](SPEC-emprunts.md) | Emprunts, retours, prolongations, retards, pertes | catalogue, adherents, penalites (RG-35 à RG-37) |
| 5 | `reservations` | [SPEC-reservations.md](SPEC-reservations.md) | File d'attente, mise de côté, expiration | catalogue, adherents, emprunts |
| 6 | `penalites` | [SPEC-penalites.md](SPEC-penalites.md) | Calcul, encaissement, remise, blocage | emprunts |
| 7 | `pilotage` | [SPEC-pilotage.md](SPEC-pilotage.md) | Tableau de bord, statistiques, paramétrage, audit | toutes |

> `emprunts` et `penalites` sont fortement couplées : la pénalité de retard est créée au retour (US-037). Il est recommandé de les concevoir (UX + ARCH) ensemble.

## 3. Convention de numérotation
Les identifiants sont **uniques à l'échelle de l'application** (et donc a fortiori par feature) afin que les specs se référencent sans ambiguïté :
| Feature | US | RG | Q |
|---|---|---|---|
| authentification | US-001 → US-006 | RG-01 → RG-06 | Q-01 → Q-02 |
| catalogue | US-007 → US-015 | RG-07 → RG-12 | Q-03 → Q-04 |
| adherents | US-016 → US-024 | RG-13 → RG-20 | Q-05 → Q-08 |
| emprunts | US-025 → US-031 | RG-21 → RG-28 | Q-09 → Q-12 |
| reservations | US-032 → US-036 | RG-29 → RG-34 | Q-13 → Q-14 |
| penalites | US-037 → US-040 | RG-35 → RG-39 | Q-15 → Q-17 |
| pilotage | US-041 → US-044 | RG-40 → RG-41 | Q-18 |

Toute nouvelle spec poursuit la numérotation (prochains libres : **US-045, RG-42, Q-19, NFR-13**).

## 4. Acteurs
| Acteur | Rôle applicatif | Description |
|---|---|---|
| Visiteur | aucun (non connecté) | Consulte le catalogue public. |
| Adhérent | `MEMBER` | Personne inscrite ; consulte son espace, prolonge ses emprunts, réserve. |
| Bibliothécaire | `LIBRARIAN` | Agent de guichet ; gère catalogue, adhérents, circulation, encaissements. |
| Administrateur | `ADMIN` | Responsable ; possède tous les droits du bibliothécaire, plus comptes du personnel, paramètres, remises gracieuses, statistiques. |

## 5. Machine d'états de l'exemplaire (vue transverse)
| De | Vers | Déclencheur | Spec |
|---|---|---|---|
| `AVAILABLE` | `ON_LOAN` | Emprunt enregistré | emprunts (US-025) |
| `ON_LOAN` | `AVAILABLE` | Retour sans réservation en attente | emprunts (US-026) |
| `ON_LOAN` | `RESERVED` | Retour avec réservation `PENDING` sur le livre | emprunts (US-026) / reservations |
| `RESERVED` | `ON_LOAN` | Emprunt par l'adhérent bénéficiaire | emprunts (US-025) |
| `RESERVED` | `RESERVED` | Réservation expirée/annulée, adhérent suivant dans la file | reservations (RG-33, RG-34) |
| `RESERVED` | `AVAILABLE` | Réservation expirée/annulée, file vide | reservations (RG-33, RG-34) |
| `ON_LOAN` | `LOST` | Perte déclarée | emprunts (US-029) |
| `LOST` | `AVAILABLE` | Exemplaire retrouvé | emprunts (US-030) |
| `AVAILABLE`, `LOST` | `WITHDRAWN` | Retrait du fonds | catalogue (US-013) |

## 6. Exigences non fonctionnelles transverses
| ID | Catégorie | Exigence vérifiable |
|---|---|---|
| NFR-01 | Performance | Une recherche dans un catalogue de 50 000 notices renvoie la première page de résultats en ≤ 2 s pour 95 % des requêtes, avec 100 utilisateurs connectés simultanément. |
| NFR-02 | Performance | L'enregistrement d'un emprunt ou d'un retour est confirmé en ≤ 1 s pour 95 % des opérations. |
| NFR-03 | Accessibilité | Toutes les pages respectent WCAG 2.1 niveau AA (RGAA 4.1) ; toutes les opérations du guichet sont réalisables au clavier seul. |
| NFR-04 | Compatibilité | Fonctionne sur les 2 dernières versions de Chrome, Firefox, Edge et Safari ; écrans de 360 px à 1920 px de large. |
| NFR-05 | Langue | Interface, messages et documents en français ; dates au format JJ/MM/AAAA ; montants en euros avec 2 décimales (ex. « 1,20 € »). |
| NFR-06 | Sécurité | Toute page ou donnée autre que le catalogue public exige une authentification ; un adhérent n'accède jamais aux données d'un autre adhérent. |
| NFR-07 | RGPD | Les données personnelles ne sont visibles que par le personnel (`LIBRARIAN`, `ADMIN`) et l'adhérent concerné ; durées de conservation : voir RG-19 et RG-20. |
| NFR-08 | Audit | Toute création, modification ou changement d'état d'un adhérent, emprunt, réservation, pénalité, compte ou paramètre est journalisé (auteur, date-heure, action, objet, valeurs avant/après hors mot de passe) et conservé 12 mois. |
| NFR-09 | Disponibilité | Service disponible ≥ 99 % pendant les heures d'ouverture (lundi-samedi, 8 h-20 h). |
| NFR-10 | Fiabilité | Deux opérations simultanées sur le même exemplaire ne peuvent jamais aboutir à deux emprunts actifs ou deux mises de côté du même exemplaire. |
| NFR-11 | Exactitude | Les dates d'échéance et les pénalités sont calculées en jours calendaires sur le fuseau Europe/Paris. |
| NFR-12 | Sauvegarde | Les données sont sauvegardées au moins une fois par jour ; perte de données maximale admise : 24 h. |

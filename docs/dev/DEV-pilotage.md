---
feature: pilotage
agent: developpeur-fullstack
version: 1
statut: READY
sources: [docs/specs/SPEC-pilotage.md, docs/design/UX-pilotage.md, docs/architecture/ARCH-pilotage.md, docs/architecture/openapi.yaml]
---
# DEV — Pilotage : tableau de bord, statistiques, paramètres, audit

## 1. Résumé
Mode **IMPLÉMENTATION**, itération 1. Les US-041 → US-044 sont livrées : 6 endpoints et les écrans ECR-37 → ECR-40.

Le tableau de bord devient l'accueil du personnel (`/gestion`).

## 2. Fichiers créés / modifiés
| Fichier | Type | Rôle | Réf. ARCH §6 |
|---|---|---|---|
| `backend/…/common/settings/Setting.java` | modif | `update(valeur, auteur, instant)` ; champs exposés | 1 |
| `backend/…/reporting/repository/ReportingQueries.java` | création | `JdbcClient`, SQL paramétré ; instants liés en UTC explicite | 2 |
| `backend/…/reporting/service/*` (5 types) | création | `DashboardService`, `StatisticsService` (+ CSV), `SettingsAdminService`, `AuditLogService`, `ReportingViews` | 3 |
| `backend/…/reporting/web/ReportingController.java` | création | `/dashboard`, `/statistics`, `/statistics/export`, `/settings`, `/audit-entries` | 4 |
| `backend/src/test/…` (2 classes, 11 tests) | création | `ReportingServicesTest`, `ReportingControllerTest` | — |
| `frontend/src/app/features/pilotage/**` | création | ECR-37 → ECR-40, `PilotageService` | 5 |

## 3. Traçabilité
| US / AC | Endpoint(s) | Écran | Tests |
|---|---|---|---|
| US-041 AC-041-1 → 3 | `GET /dashboard` | ECR-37 | `ReportingServicesTest.tableauDeBordDuJour`, `ReportingControllerTest` |
| US-042 AC-042-1 → 5 | `GET /statistics`, `/statistics/export` | ECR-38 | `ReportingServicesTest.statistiques*`, `…moisFuturRefuse`, `…exportTableur…` (neutralisation des formules) |
| US-043 AC-043-1 → 5 | `GET /settings`, `PUT /settings/{code}` | ECR-39 | `ReportingServicesTest.parametreModifie…` (bornes, entier, montant, version), `settings.page.spec` |
| US-044 AC-044-1 → 4 | `GET /audit-entries` | ECR-40 | `ReportingServicesTest.journalDAudit*`, `ReportingControllerTest` (LIBRARIAN → 403) |

## 4. Résultats de build et tests
| Commande | Résultat | Détails |
|---|---|---|
| `./gradlew build` | BUILD SUCCESSFUL | `reporting.service` 98,1 % |
| Parcours d'API réel | conforme (inclus dans les 86) | Tableau de bord, statistiques du mois, mois futur refusé, export CSV sans nom, 12 paramètres, quota hors bornes, durée passée à 28 jours, journal filtré, aucun mot de passe dans le journal |
| Navigateur (Cypress) | conforme | Tableau de bord, statistiques, paramètres, journal déplié |

## 5. Traitement des constats (mode CORRECTION)
N/A — première itération.

## 6. Écarts et questions
| ID | Écart / question | Hypothèse appliquée | Agent concerné |
|---|---|---|---|
| Q-43 | Prolongations par mois | Comptées dans le journal d'audit (12 mois) | concepteur-architecte |
| Q-44 | « Adhérents ayant emprunté » après l'anonymisation | Exact sur 4 mois, sous-estimé ensuite | spec-kit-ba |
| Q-45 | Filtre « Auteur » du journal | Texte libre (contient) au lieu d'une autocomplétion | designer-ux |
| — | L'UX prévoit quatre services d'accès aux données | Un seul `PilotageService` (quatre lectures simples) | — |
| — | Les exemplaires par catégorie pour la rotation sont comptés à la date du jour, pas au dernier jour du mois (RG-41) | Exact pour le mois en cours ; pour un mois passé, nécessiterait un historique des statuts | spec-kit-ba |

## 7. Points d'attention pour le Reviewer et le QA
- **Export CSV** : UTF-8 avec BOM, séparateur `;`, décimales à la française. Une cellule commençant par `=`, `+`, `-` ou `@` est préfixée d'une apostrophe (injection de formule).
- **Journal** : la requête est construite avec des fragments fixes. Seules les valeurs sont paramétrées, et l'objet recherché est aussi cherché dans les détails (`LIKE` échappé).

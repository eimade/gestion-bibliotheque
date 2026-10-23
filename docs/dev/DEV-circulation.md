---
feature: circulation
agent: developpeur-fullstack
version: 1
statut: READY
sources: [docs/specs/SPEC-emprunts.md, docs/specs/SPEC-reservations.md, docs/specs/SPEC-penalites.md, docs/design/UX-emprunts.md, docs/design/UX-reservations.md, docs/design/UX-penalites.md, docs/architecture/ARCH-circulation.md, docs/architecture/openapi.yaml, docs/dev/DEV-adherents.md]
---
# DEV — Circulation : emprunts, réservations et pénalités

## 1. Résumé
Mode **IMPLÉMENTATION**, itération 1. Ce rapport couvre les features `emprunts`, `reservations` et `penalites`, conçues ensemble (INDEX §2). Elles forment un seul module backend `circulation` et trois features frontend.

**Livré :**
- US-025 → US-035 et US-037 → US-040 ;
- 28 endpoints ;
- écrans ECR-24 → ECR-36 ;
- trois traitements planifiés (RG-27, RG-33, RG-19).

**Reporté :** US-036 (COULD, e-mail « réservation prête »), car aucun service d'envoi n'est configuré (Q-41).

## 2. Fichiers créés / modifiés
| Fichier | Type | Rôle | Réf. ARCH §7 |
|---|---|---|---|
| `backend/…/catalog/domain/Copy.java`, `repository/CopyRepository.java` | modif | `changeCirculationStatus` (jamais depuis ou vers `WITHDRAWN`) ; verrous `FOR UPDATE` ; comptages par statut | 1 |
| `backend/…/catalog/service/CopyCirculationService.java`, `CopyAllocation.java`, `CopyService.java` | création/modif | Exemplaires vus par la circulation ; AC-012-2 : un exemplaire ajouté va à la file d'attente | 1 |
| `backend/…/circulation/domain/*` (9 types) | création | `Loan` (statut effectif, jours de retard), `Reservation`, `Fine` (montant figé), `Payment` et statuts | 3 |
| `backend/…/circulation/repository/*` | création | Retards (trois tris), file (tête verrouillée), prêtes, libérées, débiteurs (trois tris), obligations groupées, anonymisation | 4 |
| `backend/…/circulation/service/*` (11 types) | création | `EligibilityService` (port `MemberCirculation`), `FineCalculator`, `LoanRenewalPolicy`, `LoanService`, `ReservationAllocator` (port `CopyAllocation`), `ReservationService`, `FineService`, `CirculationReadService`, `LoanMaintenanceService` | 5 |
| `backend/…/circulation/job/CirculationJobs.java` | création | RG-27 à 00 h 00, RG-33 à 00 h 05, RG-19 à 02 h 00 | 6 |
| `backend/…/circulation/web/*` (6 types) | création | `LoanController`, `ReservationController`, `FineController`, `MemberCirculationController`, `MySpaceCirculationController` | 7 |
| `backend/…/common/exception/GlobalExceptionHandler.java`, `security/SecurityConfig.java` | modif | `uk_loans_active_copy` → `COPY_NOT_AVAILABLE` (AC-025-10) ; `uk_reservations_active_member_book` → `RESERVATION_ALREADY_EXISTS` ; remise réservée à ADMIN | — |
| `backend/src/test/…` (8 classes, 78 tests) | création | Règles, emprunts, réservations, pénalités, lectures et traitements, contrôleurs, `CopyCirculationServiceTest` | — |
| `frontend/src/app/features/emprunts/**` | création | ECR-24, ECR-25 (clavier et lecteur), ECR-26, ECR-27, ECR-28, ECR-29, `app-renew-loan-action` | 8 |
| `frontend/src/app/features/reservations/**` | création | ECR-30 (`app-reservation-cta` sur ECR-09), ECR-31, ECR-32 (sur ECR-12), ECR-33, `app-cancel-reservation-action` | 8 |
| `frontend/src/app/features/penalites/**` | création | ECR-34 (encaissement, montants en centimes), ECR-35, ECR-36 | 8 |
| `frontend/src/app/features/catalogue/pages/book-public-detail.page.ts`, `book-admin-detail.page.ts` (+ spec) | modif | Intégration des réservations et de la circulation (Q-38 levée) | — |
| `docs/architecture/ARCH-circulation.md`, `openapi.yaml` | création/modif | Contrat produit avant le code | — |

## 3. Traçabilité
| US / AC | Endpoint(s) | Composant(s) | Test(s) unitaire(s) |
|---|---|---|---|
| US-025 AC-025-1 → 11 | `POST /loans` | ECR-24 | `LoanServiceTest` : emprunt, refus avec tous les motifs, statuts, mis de côté pour soi ou pour un autre, mise de côté échue. `CirculationRulesTest` : seuil exact, quota. `loan-desk.page.spec`. AC-025-10 : verrou de ligne + `uk_loans_active_copy` |
| US-026 AC-026-1 → 7 | `POST /returns` | ECR-25 | `LoanServiceTest.retour*` ; `return-desk.page.spec` (mise de côté en tête) |
| US-027 AC-027-1 → 7 | `POST /me/loans/{id}/renewal`, `POST /loans/{id}/renewal` | `app-renew-loan-action` (ECR-18, ECR-22) | `CirculationRulesTest.prolongation*`, `LoanServiceTest.prolongation*` (dont l'emprunt d'un autre adhérent → 404) |
| US-028 AC-028-1 → 5 | `GET /loans/overdue` | ECR-26 | `CirculationReadAndJobsTest.retardsDAuMoinsNJours…`, `…traitementsDesRetards…` |
| US-029 AC-029-1 → 3 | `GET …/loss-preview`, `POST …/loss` | ECR-27 | `LoanServiceTest.perte*` |
| US-030 AC-030-1 → 3 | `POST /copies/{n}/found` | ECR-28 | `LoanServiceTest.exemplaireRetrouve…`, `CirculationReadAndJobsTest.exemplaireLibere…` |
| US-031 AC-031-1 → 3 | `GET /copies/{n}/loans` | ECR-29 | `CirculationReadAndJobsTest.historiqueDUnExemplaire…` (emprunteur anonymisé) |
| US-032 AC-032-1 → 8 | `POST /me/reservations`, `POST /reservations`, prévisualisations | ECR-30, ECR-31 | `ReservationServiceTest` (motifs, position, quota, doublon), `reservation-cta.component.spec` |
| US-033 AC-033-1 → 4 | `POST …/cancellation` | `app-cancel-reservation-action` | `ReservationServiceTest.annulation*` |
| US-034 AC-034-1 → 3 | `GET /books/{id}/reservations`, `GET /me/books/{id}/reservation` | ECR-32, ECR-09 | `ReservationServiceTest.fileDAttente…`, `…situationDeLAdherent…` (position sans identité) |
| US-035 AC-035-1 → 5 | `GET /reservations/ready`, `/released` | ECR-33 | `ReservationServiceTest.listesATraiter…`, `…expirationDesMisesDeCote…` |
| US-037 AC-037-1 → 7 | via retour et perte | ECR-25, ECR-27, montants provisoires | `CirculationRulesTest.penalite*`, `plafond*` |
| US-038 AC-038-1 → 6 | `POST /payments` | ECR-34 | `FineServiceTest` (partiel, montant différent, déjà réglée, double encaissement), `collect-fines-dialog.component.spec` |
| US-039 AC-039-1 → 4 | `POST /fines/{id}/waiver` (ADMIN) | ECR-36 | `FineServiceTest.remiseGracieuse…`, `CirculationControllerTest.encaissementEtRemise…` (LIBRARIAN → 403) |
| US-040 AC-040-1 → 3 | `GET /fines/debtors` | ECR-35 | `FineServiceTest.debiteurs…` |

## 4. Résultats de build et tests
| Commande | Résultat | Détails |
|---|---|---|
| `./gradlew build` | BUILD SUCCESSFUL | `circulation.service` 98,2 %, `catalog.service` 98,2 % ; ArchUnit conforme (`circulation` ↛ `auth`) |
| Parcours d'API réel (pile Docker isolée) | 86 / 86 | Inclut les emprunts et refus, la file d'attente, la mise de côté au retour et son retrait, le retard simulé (date d'échéance modifiée en base), la pénalité de 1,00 €, la perte (0,80 + 20,00), le retrouvé, l'encaissement partiel refusé, la remise, les débiteurs, la prolongation par l'adhérent et le plafond |
| Navigateur (Cypress) | 2 / 2 | Guichets emprunt et retour au clavier (`Alt+E`), encaissement, file d'attente, réservations à traiter, pénalités impayées |
| Edge headless, CSP active | conforme | Fiche publique avec « Connectez-vous pour réserver » |

## 5. Traitement des constats (mode CORRECTION)
N/A — première itération.

## 6. Écarts et questions
| ID | Écart / question | Hypothèse appliquée | Agent concerné |
|---|---|---|---|
| Q-41 | US-036 (e-mail « réservation prête », COULD) | Reportée : pas de service d'envoi ; le marqueur `ready-email-failed` est prêt côté écran | spec-kit-ba |
| Q-42 | Destination d'un exemplaire libéré (ECR-33) | Lue sur l'état actuel de l'exemplaire | designer-ux |
| — | ECR-29 est prévu en panneau latéral (`app-drawer`) | Panneau modal (`app-dialog`) : même accessibilité, sans nouveau composant socle | designer-ux |
| — | Les `data-testid` de la confirmation des paramètres suivent la convention de `app-confirm-dialog` (`setting-confirm-dialog`, `-confirm`, `-cancel`) | Inchangé | qa-testeur |
| — | Le scanner sonore du socle (« retour sonore optionnel ») n'est pas implémenté | Désactivé par défaut selon l'UX ; non livré | — |

## 7. Points d'attention pour le Reviewer et le QA
- **Ordre des verrous** : adhérent puis exemplaire (emprunt) ; exemplaire puis tête de file (retour, perte, retrouvé, annulation). Concurrence à tester avec Testcontainers : deux emprunts du même exemplaire, deux encaissements de la même pénalité.
- **Statut effectif** : une échéance dépassée vaut retard, et une mise de côté échue est expirée sur-le-champ lors d'un emprunt (ADR-006 §3). Les traitements de minuit ne sont donc pas nécessaires à l'exactitude des lectures.
- **Montants** : `BigDecimal` à l'échelle 2 côté serveur, centimes entiers côté client.
- **Prolongations** : elles sont comptées par le journal d'audit (`details.event = "RENEWAL"`), utilisé par les statistiques (Q-43).

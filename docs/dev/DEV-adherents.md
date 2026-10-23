---
feature: adherents
agent: developpeur-fullstack
version: 1
statut: READY
sources: [docs/specs/SPEC-adherents.md, docs/design/UX-adherents.md, docs/design/UX-socle.md, docs/architecture/ARCH-adherents.md, docs/architecture/ARCH-circulation.md, docs/architecture/openapi.yaml, docs/dev/DEV-socle.md]
---
# DEV — Adhérents et espace adhérent

## 1. Résumé
Mode **IMPLÉMENTATION**, itération 1.

Les US-016 à US-024 sont livrées de bout en bout : 12 endpoints et les écrans ECR-16 à ECR-23. Le contrat a d'abord été écrit en rôle Architecte : [ARCH-adherents.md](../architecture/ARCH-adherents.md) et `openapi.yaml`.

La fiche adhérent (ECR-18) regroupe les actions de circulation : emprunt, prolongation, perte, annulation de réservation, encaissement, remise. Ces actions sont décrites dans [DEV-circulation.md](DEV-circulation.md).

## 2. Fichiers créés / modifiés
| Fichier | Type | Rôle | Réf. ARCH §7 |
|---|---|---|---|
| `backend/…/auth/domain/UserAccount.java` | modif | Fabrique `member`, `changeEmail`, `anonymizeForArchive` (RG-18) | 1 |
| `backend/…/auth/service/MemberAccountService.java` | création | Compte adhérent : création, e-mail de connexion (RG-14), archivage, lectures groupées | 1 |
| `backend/…/member/domain/*` (5 types) | création | `Member` : statut effectif RG-16 ; transitions renouveler, suspendre, réactiver, expirer, archiver. `Guardian` est embarqué | 2 |
| `backend/…/member/repository/*` | création | Recherche JPQL (e-mail par sous-requête, Q-40), verrou de ligne, homonymes, adhérents à archiver, lot d'expiration ; générateur `A%06d` | 3 |
| `backend/…/member/service/*` (10 types) | création | `MembershipPolicy` (RG-15, RG-20), `MemberService`, `MemberSpaceService`, `MemberLookup` (pour la circulation), port `MemberCirculation`, `MembershipExpiryService` | 4 |
| `backend/…/member/job/MembershipExpiryJob.java` | création | RG-16, 00 h 10, par lots de 500 | 5 |
| `backend/…/member/web/*` | création | `MemberController` (`/members`), `MemberSpaceController` (`/me/member`, `/me/contact`), requêtes validées | 6 |
| `backend/…/common/config/AppProperties.java` | modif | `privacy` (RG-19 : D1, D2) | — |
| `backend/…/common/security/SecurityConfig.java` | modif | `/members/**` réservé au personnel, `/me/**` à l'adhérent | — |
| `backend/src/test/…` (5 classes, 49 tests) | création | `MembershipPolicyTest`, `MemberServiceTest`, `MemberSpaceAndLookupTest`, `MemberControllerTest`, `MemberAccountServiceTest` | — |
| `frontend/src/app/features/adherents/**` (données, 3 composants, 6 pages) | création | ECR-16 → ECR-23 ; `app-eligibility-panel` réutilisé par ECR-24 | 7 |
| `frontend/src/app/shared/validators/member.validators.ts`, `pipes/euro.pipe.ts`, `pipes/date-fr.pipe.ts`, `ui/tabs`, `ui/scan-input`, `ui/stat-card`, `models/statuses.ts` | création/modif | Téléphone (Q-24), code postal, date passée, âge ; euros ; date-heure de Paris ; onglets ; champ de scan ; carte d'indicateur | — |
| `frontend/src/app/app.routes.ts`, `core/layout/navigation.ts`, `core/layout/staff-layout.component.ts` | modif | Routes `/gestion/adherents/**`, `/mon-espace`, `/mon-espace/coordonnees` ; menus ; raccourcis `Alt+E/R/A/C` (Q-20) | — |
| `docs/architecture/ARCH-adherents.md`, `openapi.yaml` | création/modif | Contrat produit avant le code (Redocly : valide) | — |

## 3. Traçabilité
| US / AC | Endpoint(s) | Composant(s) | Test(s) unitaire(s) |
|---|---|---|---|
| US-016 AC-016-1 → 8 | `POST /members` | ECR-17 `member-form`, dialogue homonyme | `MemberServiceTest.inscrit…`, `…refuseUnMineur…`, `…signaleUnHomonyme…`, `…refuseUnEmailDejaUtilise` ; `MemberControllerTest.inscription*` ; `member.validators.spec` (âge le jour anniversaire) |
| US-017 AC-017-1 → 5 | `GET /members` | ECR-16 (un numéro ouvre la fiche) | `MemberServiceTest.recherche*`, `MemberControllerTest.rechercheAvecTri…` |
| US-018 AC-018-1 → 5 | `GET /members/{n}` + onglets (DEV-circulation) | ECR-18, `app-eligibility-panel` | `MemberServiceTest.ficheIntrouvable`, `…unAdherentActifEchu…` ; `member-detail.page.spec` (motifs, retard, 404, archivé) ; `MemberControllerTest` (MEMBER → 403) |
| US-019 AC-019-1 → 5 | `PUT /members/{n}` | ECR-17 (modification) | `MemberServiceTest.modifieEtJournalise…`, `…refuseDeModifierUnArchive…` |
| US-020 AC-020-1 → 5 | `POST /members/{n}/renewal` | ECR-18 (confirmation, date possible) | `MembershipPolicyTest` (5 cas), `MemberServiceTest.renouvelle…`, `…refuseUnRenouvellement…` ; `member-detail.page.spec.renouvelle…` |
| US-021 AC-021-1 → 5 | `POST …/suspension`, `…/reactivation` | ECR-19 | `MemberServiceTest.suspendPuisReactive…`, `…reactiverUnArchive…` |
| US-022 AC-022-1 → 4 | `GET …/archive-check`, `POST …/archive`, `GET /members/to-archive` | ECR-20 (saisie « ARCHIVER »), ECR-21 | `MemberServiceTest.archivage*`, `…verifieLArchivabilite…` ; `MemberAccountServiceTest.archive…` |
| US-023 AC-023-1 → 5 | `GET /me/member` + `/me/loans|reservations|fines|history` | ECR-22 | `MemberSpaceAndLookupTest`, `CirculationControllerTest.espaceAdherent…` |
| US-024 AC-024-1 → 3 | `PUT /me/contact` | ECR-23 | `MemberControllerTest.lAdherentNePeutPasModifierSonNom…` (propriété inconnue → 400, code postal) |

## 4. Résultats de build et tests
| Commande | Résultat | Détails |
|---|---|---|
| `./gradlew build` | BUILD SUCCESSFUL | 329 tests backend (128 nouveaux), aucun échec. JaCoCo : `member.service` 98,3 %, `auth.service` 99,3 %. ArchUnit conforme (`member` → `auth` seulement) |
| `npx ng build` | réussi, aucun avertissement | — |
| `npx ng test --watch=false` | 121 / 121 | 22 fichiers, dont 32 nouveaux tests (toutes features) |
| Parcours d'API réel (pile Docker isolée, base vierge) | 86 / 86 vérifications conformes | Section adhérents : inscription et `Location`, homonyme, e-mail sans tenir compte de la casse, mineur, recherche, 404, renouvellement trop tôt, modification concurrente, suspension et réactivation, archivage bloqué puis réussi, compte archivé inutilisable, champ réservé refusé (AC-024-2) |
| Parcours navigateur (Cypress sur la pile isolée) | 2 / 2 | Fiche adhérent, inscription d'un mineur, encaissement, espace adhérent, coordonnées |

## 5. Traitement des constats (mode CORRECTION)
N/A — première itération.

## 6. Écarts et questions
| ID | Écart / question | Hypothèse appliquée | Agent concerné |
|---|---|---|---|
| Q-40 | La recherche par e-mail lit `user_accounts` depuis le module `member` | Sous-requête JPQL sur l'entité `UserAccount` (dépendance `member` → `auth` autorisée) | code-reviewer |
| — | `PUT /me/contact` avec une propriété inconnue | Refus 400 grâce à `fail-on-unknown-properties` (AC-024-2) ; ARCH-adherents §3 corrigé dans ce sens | — |
| — | Le message « Renouvellement possible à partir du … » est renvoyé au format JJ/MM/AAAA, avec `renewableFrom` en ISO | Le frontend reformate à partir de `renewableFrom` | — |
| — | La page d'attente des accueils `/gestion` et `/mon-espace` (Q-33) est supprimée | Remplacée par le tableau de bord (ECR-37) et l'espace adhérent (ECR-22) | — |

## 7. Points d'attention pour le Reviewer et le QA
- **Verrou de ligne `members`** (`PESSIMISTIC_WRITE`), partagé par l'archivage, l'emprunt et la réservation. À tester en intégration : un archivage concurrent d'un emprunt.
- **Statut effectif** : un adhérent `ACTIVE` dont l'expiration est passée est présenté `EXPIRED` (fiche, recherche, éligibilité), même avant le traitement de 00 h 10.
- **Audit** : les modifications enregistrent les valeurs avant et après des seuls champs changés. L'auteur adhérent apparaît comme « Adhérent A000123 ».
- **Mot de passe initial** : il est vérifié (RG-01) avant toute écriture. Une inscription refusée ne laisse donc ni adhérent ni compte.

---
feature: authentification
agent: developpeur-fullstack
version: 1
statut: READY
sources: [docs/specs/SPEC-authentification.md, docs/design/UX-authentification.md, docs/design/UX-socle.md, docs/architecture/ARCH-authentification.md, docs/architecture/openapi.yaml, docs/architecture/adr/ADR-004-securite-session-serveur.md, docs/dev/DEV-socle.md]
---
# DEV — Authentification et comptes utilisateurs

## 1. Résumé
Mode **IMPLÉMENTATION**, itération 1. Les 6 US de la feature sont livrées de bout en bout : 9 endpoints conformes à `openapi.yaml`, les écrans ECR-03 à ECR-07, l'administrateur initial au démarrage et la journalisation de chaque opération. Le socle technique utilisé est décrit dans [DEV-socle.md](DEV-socle.md).

## 2. Fichiers créés / modifiés
| Fichier | Type | Rôle | Réf. ARCH §7 |
|---|---|---|---|
| `backend/…/auth/domain/AccountRole.java`, `AccountStatus.java` | création | Énumérations alignées sur les `CHECK` SQL | domain |
| `backend/…/auth/domain/UserAccount.java` | création | Entité + transitions (verrouillage, déverrouillage, désactivation, réactivation, changement de mot de passe) | domain |
| `backend/…/auth/domain/PasswordHistoryEntry.java` | création | Empreintes précédentes (RG-01) | domain |
| `backend/…/auth/repository/UserAccountRepository.java`, `PasswordHistoryEntryRepository.java` | création | Recherche paginée du personnel (JPQL paramétré, `LIKE` échappé) ; verrou `PESSIMISTIC_WRITE` des ADMIN | repository |
| `backend/…/auth/service/PasswordPolicy.java`, `PasswordService.java` | création | RG-01 (politique, historique de 3), RG-05 | service |
| `backend/…/auth/service/AuthenticationService.java`, `LoginOutcome.java`, `SessionUser.java`, `SessionUserService.java` | création | Connexion avec verrouillage RG-02 (refus = résultat validé en base, pas exception) ; rejeu unique en cas de concurrence | service |
| `backend/…/auth/service/StaffAccountService.java`, `StaffAccountView.java`, `CreateStaffAccountCommand.java` | création | US-004, US-005, RG-04, RG-06, RG-14 | service |
| `backend/…/auth/service/AccountPasswordResetService.java` | création | US-006 | service |
| `backend/…/auth/service/BootstrapAdminInitializer.java`, `AccountLabels.java`, `MemberDirectory.java` | création | Administrateur initial (ADR-004 §10) ; libellés d'audit ; port vers le module `member` | service |
| `backend/…/auth/web/AuthController.java`, `StaffAccountController.java`, `AccountController.java` + 6 DTO | création | Endpoints ARCH-authentification §3 ; `toString()` des DTO sans mot de passe | web |
| `backend/src/test/java/…/auth/**` (11 classes, 87 tests) | création | Tests unitaires services et contrôleurs | — |
| `frontend/src/app/features/authentification/data-access/*` (5 fichiers) | création | Services HTTP typés, modèles miroir des DTO, messages exacts | data-access |
| `frontend/src/app/features/authentification/components/*` (6 composants) | création | `login-form`, `password-form`, `password-policy-checklist`, `staff-account-table`, `staff-account-form-dialog` (ECR-06), `reset-password-dialog` (ECR-07, autonome et réutilisable) | components |
| `frontend/src/app/features/authentification/pages/*` (3 pages) | création | ECR-03, ECR-04, ECR-05 | pages |
| `frontend/src/app/features/authentification/**/*.spec.ts` (5 fichiers, 33 tests) | création | Tests unitaires de la feature (socle : 5 fichiers, 35 tests, voir DEV-socle) | — |
| `docs/architecture/ARCH-authentification.md`, `openapi.yaml` | création/modif | Contrat produit en rôle Architecte avant le code (ajout de `GET /staff-accounts/{id}`, cible de `Location`) | — |

## 3. Traçabilité
| US / AC | Endpoint(s) | Composant(s) | Test(s) unitaire(s) |
|---|---|---|---|
| US-001 AC-001-1/2 (connexion, accueil par rôle) | `POST /auth/login` | `app-login-page`, `app-login-form`, `homeUrlFor` | `AuthenticationServiceTest.connecteAvec…`, `AuthControllerTest.connexionReussie…`, `login.page.spec` (accueil, retour interne) |
| AC-001-3 (identifiants incorrects, sans révéler l'e-mail) | `POST /auth/login` → 401 `INVALID_CREDENTIALS` | message exact | `AuthenticationServiceTest.refuseUnEmailInconnuSansReveler`, `…compteLesEchecs…`, `auth-messages.spec` |
| AC-001-4 (verrouillage au 5ᵉ échec, 15 min) | 401 `ACCOUNT_LOCKED` + `lockedUntil` | message avec heure de Paris | `AuthenticationServiceTest.verrouilleAuCinquiemeEchec…`, `…refuseLeBonMotDePassePendantLeVerrouillage`, `AuthControllerTest.compteVerrouille…` |
| AC-001-5 (compte désactivé) | 401 `ACCOUNT_DISABLED` | message exact | `AuthenticationServiceTest.refuseUnCompteDesactive…`, `…neRevelePasLaDesactivation…`, `login.page.spec` |
| AC-001-6 (fin du verrouillage) | `POST /auth/login` | — | `AuthenticationServiceTest.leveLeVerrouEchu…` |
| AC-001-7 (mot de passe temporaire) | filtre RG-05 → 403 `PASSWORD_CHANGE_REQUIRED` | `passwordChangeGuard`, ECR-04 mode obligatoire | `AuthControllerTest.motDePasseTemporaire*`, `guards.spec`, `login.page.spec` |
| AC-001-8 (inactivité) | session 30 min → 401 | `SessionIdleService`, dialogue d'expiration | `session-idle.service.spec`, `problem-detail.interceptor.spec` (401 → connexion) |
| US-002 AC-002-1/2 (déconnexion) | `POST /auth/logout` | `app-user-menu`, `SignOutService` | `AuthControllerTest.deconnexionInvalideLaSession`, `auth.service.spec`, `guards.spec` (authGuard) |
| US-003 AC-003-1 → AC-003-5 | `POST /auth/password` | `app-change-password-page`, `app-password-form`, checklist | `PasswordServiceTest` (10 tests), `AuthControllerTest.changementDeMotDePasse*`, `password-policy.validator.spec` |
| US-004 AC-004-1 → AC-004-4 | `GET/POST /staff-accounts`, `GET /staff-accounts/{id}` | ECR-05, ECR-06 | `StaffAccountServiceTest.cree…`, `…refuseUnEmailDejaUtilise`, `…refuseDeCreerUnCompteAdherent`, `StaffAccountControllerTest` (201 + `Location`, 403 bibliothécaire), `staff-accounts.page.spec` (e-mail déjà utilisé) |
| US-005 AC-005-1 → AC-005-5 | `POST /staff-accounts/{id}/deactivation`, `/reactivation` | menu ⋮, confirmation | `StaffAccountServiceTest` (soi-même, dernier admin, sessions supprimées, réactivation), `staff-account-table.component.spec`, `staff-accounts.page.spec` |
| US-006 AC-006-1 → AC-006-3 (SHOULD) | `POST /accounts/{id}/password-reset` | ECR-07 | `AccountPasswordResetServiceTest` (4 tests), `AccountControllerTest` (3 tests) |

## 4. Résultats de build et tests
Voir [DEV-socle §4](DEV-socle.md) (builds communs). Spécifique à la feature :
| Commande | Résultat | Détails |
|---|---|---|
| Tests backend de la feature (`auth.*`) | 87 / 87 | sur 109 au total ; `auth.service` couvert à 100 % des lignes |
| Tests frontend de la feature (`features/authentification`) | 33 / 33 | sur 68 au total ; data-access, composants et pages |
| Parcours d'API réel (pile Docker, `curl` avec session et CSRF) | 23 vérifications conformes | dont verrouillage au 5ᵉ échec, refus du bon mot de passe pendant le verrouillage, réutilisation refusée, doublon d'e-mail insensible à la casse |
| Parcours navigateur réel (Cypress/Electron via nginx) | réussi | ECR-03 → ECR-04 obligatoire → ECR-05/06 → désactivation → déconnexion → compte désactivé refusé → ECR-01 |

## 5. Traitement des constats (mode CORRECTION)
N/A — première itération.

## 6. Écarts et questions
| ID | Écart / question | Hypothèse appliquée | Agent concerné |
|---|---|---|---|
| — | **Corrigé pendant le développement** : la réponse de désactivation/réactivation renvoyait une `version` périmée (vue construite avant l'écriture) | `flush()` avant de construire la réponse ; test `desactiveUnBibliothecaire…` vérifie l'appel | — |
| — | **Corrigé pendant le développement** : après un refus de connexion, « Ce champ est obligatoire. » s'affichait sous le mot de passe vidé | Seul le message du serveur est affiché ; test ajouté | — |
| — | Un chemin hors des familles d'URL déclarées renvoie 401/403 et non 404 `ROUTE_NOT_FOUND` (refus par défaut) | Choix de sécurité documenté par l'Architecte dans ARCH-authentification §3 | — |
| Q-31 | RG-06 : un ADMIN `LOCKED` compte-t-il comme actif ? | Oui (ARCH-authentification §10) | spec-kit-ba |
| Q-33 | Accueils `/gestion` et `/mon-espace` non livrés | Page d'attente (voir DEV-socle §6) | designer-ux |

## 7. Points d'attention pour le Reviewer et le QA
- **Concurrence** : rejeu unique de la connexion en cas de verrou optimiste (`AuthenticationServiceTest.rejoueUneFois…`) ; dernier administrateur protégé par `SELECT … FOR UPDATE` : à tester en intégration avec deux désactivations simultanées.
- **Révocation des sessions** (AC-005-1) : `SessionRevoker` interroge Spring Session JDBC par nom de principal (identifiant du compte). Non testable unitairement : test d'intégration QA recommandé (session ouverte, désactivation, requête suivante → 401).
- **Données personnelles** : le journal d'audit ne contient jamais de mot de passe ; pour un adhérent, l'auteur est « Adhérent A000123 » (sans nom) ; une tentative sur un e-mail inconnu est journalisée sans l'e-mail.
- **`data-testid`** : ceux de l'UX sont respectés ; ajouts : `staff-accounts-self-disable-reason`, `disable-account-dialog|confirm|cancel`, `staff-accounts-search-input|submit|error`, `staff-accounts-pagination-prev|next|size`, `user-menu*`, `session-expiry-*`, `nav-*`, `pending-feature`.
- **Adhérents** : le compte `MEMBER` n'est créé qu'à l'inscription (feature adherents) ; ECR-07 est prêt à être intégré à la fiche adhérent (ECR-18).

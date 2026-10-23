---
feature: socle
agent: developpeur-fullstack
version: 1
statut: READY
sources: [docs/architecture/ARCH-socle.md, docs/architecture/ARCH-authentification.md, docs/architecture/adr/ADR-001-architecture-backend-modulaire.md, docs/architecture/adr/ADR-002-donnees-mariadb-flyway.md, docs/architecture/adr/ADR-003-api-rest-problem-detail.md, docs/architecture/adr/ADR-004-securite-session-serveur.md, docs/architecture/adr/ADR-005-build-et-deploiement.md, docs/architecture/adr/ADR-006-traitements-planifies.md, docs/design/UX-socle.md]
---
# DEV — Socle technique (backend et frontend)

## 1. Résumé
Mode **IMPLÉMENTATION**, itération 1. Socle transverse sur lequel s'appuient toutes les features :
- **Backend** (ARCH-socle §7.2) : configuration (horloge, temps métier, propriétés, planification), erreurs `ProblemDetail` avec code métier, pagination validée, sécurité par session serveur + CSRF pour application monopage, filtre « mot de passe temporaire », révocation des sessions, journal d'audit et sa purge, lecture des paramètres RG-40, règles d'architecture ArchUnit.
- **Frontend** (ARCH-socle §7.3, UX-socle) : projet Angular 22 `bibliotheque-frontend` (standalone, sans Zone.js, `OnPush`, signals), Tailwind CSS 3.4 avec les tokens d'UX-socle §5.1, gabarits public, adhérent et personnel, guards, intercepteurs, notifications, avertissement d'inactivité, composants partagés, pages ECR-01 et ECR-02, outillage Cypress pour le QA.
La feature authentification livrée en même temps fait l'objet de [DEV-authentification.md](DEV-authentification.md).

## 2. Fichiers créés / modifiés
### Backend (`backend/src/main/java/com/bibliotheque/`)
| Fichier | Type | Rôle | Réf. ARCH |
|---|---|---|---|
| `common/config/AppProperties.java` | création | Propriétés `app.*` typées (record) | socle §7.2 |
| `common/config/ClockConfig.java` | création | Bean `Clock` UTC | socle §7.2, ADR-001 §6 |
| `common/config/BusinessTime.java` | création | Instant UTC et date métier Europe/Paris (NFR-11) | ADR-001 §6 |
| `common/config/SchedulingConfig.java` | création | `@EnableScheduling` | ADR-006 |
| `common/exception/ErrorCode.java` | création | Catalogue complet des codes (identique à `openapi.yaml`) | socle §4 |
| `common/exception/BusinessException.java` | création | Refus métier immuable (code, statut, motifs, propriétés) | ADR-003 |
| `common/exception/ProblemDetails.java`, `FieldErrorView.java` | création | Fabrique de `ProblemDetail` ; erreurs de champ | ADR-003 |
| `common/exception/GlobalExceptionHandler.java` | création | Traduction unique : métier, validation, verrou optimiste et contraintes → 409, erreurs Spring MVC → code, 500 sans détail technique | ADR-003, socle §5 |
| `common/web/PageResponse.java`, `PageRequests.java`, `LikePatterns.java` | création | Page commune ; pagination validée (taille 1-100, tri en liste blanche) ; motifs `LIKE` échappés | ADR-003 §5 |
| `common/security/SecurityConfig.java` | création | Chaîne de filtres, refus par défaut, 401/403 en `ProblemDetail`, BCrypt délégué | ADR-004, socle §6 |
| `common/security/SpaCsrfTokenRequestHandler.java` | création | CSRF cookie `XSRF-TOKEN` / en-tête `X-XSRF-TOKEN` pour Angular | ADR-004 §3 |
| `common/security/PasswordChangeRequiredFilter.java`, `ProblemDetailWriter.java` | création | RG-05 ; écriture d'un `ProblemDetail` hors MVC | ADR-004 §7 |
| `common/security/AccountPrincipal.java`, `SecuritySessionManager.java`, `SessionRevoker.java` | création | Principal de session (nom = id du compte), ouverture avec changement d'identifiant de session, révocation de toutes les sessions d'un compte | ADR-004 §4-5 |
| `common/audit/*` (6 fichiers) | création | Entité immuable, écriture dans la transaction métier, purge par lots de 500 | NFR-08, ADR-006 |
| `common/settings/*` (5 fichiers) | création | Lecture typée des 12 paramètres (`int`, montant à 2 décimales) | RG-40 |
| `member/repository/MemberDirectoryJdbcAdapter.java` | création | Identité d'adhérent fournie au module `auth` (port `MemberDirectory`) | ADR-001 §3 |
| `src/test/java/com/bibliotheque/ArchitectureTest.java` | création | Règles d'ADR-001 (modules, couches, injection par constructeur) | socle §7.2 |
| `src/test/java/com/bibliotheque/support/*` | création | Horloge de test, données de test | — |

### Frontend (`frontend/`)
| Fichier | Type | Rôle | Réf. |
|---|---|---|---|
| `package.json`, `package-lock.json`, `angular.json`, `tsconfig*.json` | création | Angular 22.2, `strict` + `strictTemplates`, proxy `/api` → `:8081` en développement | socle §7.3 |
| `tailwind.config.js`, `src/styles.css`, `src/index.html` | création/modif | Tokens UX-socle §5.1 (alias de palette, aucune couleur en dur), anneau de focus global, `lang="fr"` | UX-socle §5.1 |
| `src/app/app.ts`, `app.config.ts`, `app.routes.ts` | création/modif | Racine, intercepteurs, XSRF, locale `fr-FR`, chargement de session au démarrage, plan de navigation | UX-socle §2 |
| `src/app/core/auth/*` | création | `AuthService`, `SessionIdleService`, `SignOutService`, 4 guards | UX-socle §2.3, §5.5 |
| `src/app/core/http/*` | création | `ApiError`, messages transverses exacts, intercepteurs ProblemDetail et activité de session | UX-socle §5.6 |
| `src/app/core/layout/*` | création | Gabarits public, adhérent, personnel, compte ; en-tête, menu utilisateur, lien d'évitement, navigation, dialogue d'expiration, page d'attente | UX-socle §2.1 |
| `src/app/core/errors/error-pages.ts` | création | ECR-01, ECR-02 | UX-socle §3 |
| `src/app/core/notifications/toast.service.ts` | création | Messages globaux | UX-socle §5.3 |
| `src/app/shared/ui/*` (12 composants) | création | Bouton, alerte, champ, dialogue natif, confirmation, badge, pagination, états chargement/vide/erreur, recherche, menu d'actions, en-tête de page, notifications | UX-socle §5.3 |
| `src/app/shared/models/*`, `shared/validators/*` | création | Statuts et libellés ; politique RG-01 et confirmation | UX-socle §5.2 |
| `cypress.config.ts`, `cypress/support/e2e.ts`, `cypress/tsconfig.json` | création | Outillage E2E pour le QA | ADR-005 |
| `frontend/Dockerfile` | modif | `CYPRESS_INSTALL_BINARY=0` à la construction | ARCH-authentification §7 |

## 3. Traçabilité
| Exigence | Implémentation | Test(s) |
|---|---|---|
| NFR-06 (refus par défaut) | `SecurityConfig` : `anyRequest().denyAll()` | `StaffAccountControllerTest.anonymeNonAuthentifie`, test E2E du développeur |
| NFR-08 (audit) | `AuditService`, `AuditPurgeJob` | Tests des services auth (vérifient chaque écriture) ; purge : QA (Testcontainers) |
| NFR-11 (heure de Paris) | `BusinessTime`, `lockedMessage` (front) | `auth-messages.spec.ts` (14:15 à Paris pour 12:15 UTC) |
| RG-03 + Q-19 (inactivité) | `server.servlet.session.timeout=30m` ; `SessionIdleService` | `session-idle.service.spec.ts` (6 tests) |
| RG-05 (mot de passe temporaire) | `PasswordChangeRequiredFilter`, `passwordChangeGuard` | `AuthControllerTest.motDePasseTemporaire*`, `guards.spec.ts` |
| ADR-003 (erreurs, pagination) | `GlobalExceptionHandler`, `PageRequests`, `problemDetailInterceptor` | `PageRequestsTest`, `StaffAccountControllerTest` (409, 500), `problem-detail.interceptor.spec.ts` (7 tests) |
| ADR-004 (CSRF) | `SpaCsrfTokenRequestHandler`, `withXsrfConfiguration`, rejeu sur `CSRF_TOKEN_INVALID` | `AuthControllerTest.connexionSansJetonCsrfEstRefusee`, `problem-detail.interceptor.spec.ts` |
| ADR-001 (architecture) | Packages, port `MemberDirectory` | `ArchitectureTest` |
| WCAG (UX-socle §6) | Lien d'évitement, `<dialog>` natif, `aria-live`, badges textuels, `aria-sort`, menu ARIA au clavier | `staff-account-table.component.spec.ts` ; audit complet : QA |

## 4. Résultats de build et tests
Exécutés le 18/10/2026 sur le poste de développement.
| Commande | Résultat | Détails |
|---|---|---|
| `cd backend && ./gradlew clean build` | **BUILD SUCCESSFUL** | 109 tests, 0 échec (socle + authentification) ; vérification JaCoCo ≥ 80 % sur `*.service` réussie |
| Couverture JaCoCo (lignes) | — | `auth.service` 100 % · `auth.domain` 95 % · `auth.web` 89 % · `common.security` 95 % · `common.settings` 95 % · `common.exception` 94 % · `common.web` 97 % · `common.config` 69 % · `common.audit` 30 % · `member.repository` 0 % (adaptateur JDBC : test d'intégration QA) |
| `cd frontend && npx ng build` | **OK** | Aucun avertissement ; 315 ko initial (87 ko transférés) ; pages en chargement différé |
| `cd frontend && npx ng test --watch=false` | **OK** | 10 fichiers, 68 tests, 0 échec (Vitest + jsdom) |
| `docker compose config -q` | OK | Inchangé depuis l'Architecte, revérifié |
| `docker compose up -d --build` (base vide) | **4 services healthy** | Image frontend construite pour la première fois |
| Démarrage réel | OK | `ddl-auto=validate` accepte toutes les entités sur MariaDB 11.4 ; administrateur initial créé |
| Parcours d'API réel via `curl` (session + CSRF) | 23/23 conformes | Codes HTTP et `code` identiques au contrat ; 2 écarts trouvés et traités (DEV-authentification §6) |
| Parcours navigateur réel (Cypress/Electron, via nginx) | **1/1 réussi** | Scénario jetable du développeur, hors dépôt : connexion → changement obligatoire → comptes → déconnexion |

## 4 bis. Commande de test frontend
L'agent prévoit `npx ng test --watch=false --browsers=ChromeHeadless` (Karma). Angular 22 génère par défaut **Vitest + jsdom** : la commande est `npx ng test --watch=false` (script `npm run test:ci`). Aucun navigateur n'est nécessaire pour les tests unitaires (Q-35).

## 5. Traitement des constats (mode CORRECTION)
N/A — première itération.

## 6. Écarts et questions
| ID | Écart / question | Hypothèse appliquée | Agent concerné |
|---|---|---|---|
| — | **Corrigé après livraison (signalé par l'utilisateur)** : en production, la page s'affichait sans style dans un vrai navigateur. Le CSS critique intégré par Angular charge la feuille complète via un script en ligne, bloqué par la CSP `script-src 'self'`. Cypress désactive la CSP, d'où la non-détection | `angular.json` (production) : `optimization.styles.inlineCritical = false` ; vérifié par capture Microsoft Edge en mode headless, CSP active | qa-testeur : vérifier le rendu avec un navigateur qui applique la CSP |
| Q-32 | La purge du journal d'audit (ADR-006 §2) devait laisser un résumé dans l'audit, mais la liste fermée `ck_audit_entries_object_type` ne contient pas de type adapté | Résumé dans le journal applicatif uniquement ; un type `AUDIT` demanderait une migration V3 | concepteur-architecte |
| Q-33 | Les accueils `/gestion` (ECR-37, pilotage) et `/mon-espace` (ECR-22, adherents), ainsi que le catalogue public (accueil des visiteurs et cible de la déconnexion), ne sont pas encore livrés | Page d'attente « Cet écran sera disponible dans une prochaine version. » ; `/` et la déconnexion mènent à `/connexion` ; le menu ne montre que les entrées livrées | designer-ux (validation), à retirer à la livraison des features |
| Q-34 | Le wrapper Gradle a été **modifié en dehors de mes actions** le 18/10/2026 à 10:11 (8.14.3 → **9.7.1**, probablement une extension de l'IDE) ; l'image Docker et ADR-005 indiquent 8.14.3 | Laissé en l'état (le build passe en 9.7.1) ; à trancher : revenir à 8.14.3 ou aligner l'ADR-005 et l'image `gradle` | humain, puis concepteur-architecte |
| Q-35 | Angular 22 utilise Vitest au lieu de Karma (commande de l'agent obsolète) | Vitest + jsdom ; tests de composants exécutés sans navigateur | qa-testeur (information) |
| Q-36 | UX-socle §5.3 prévoit un `app-data-table` générique | Non créé : le tableau ECR-05 est un `<table>` sémantique dans `app-staff-account-table` (caption, `scope`, `aria-sort`) ; le composant générique sera extrait quand une 2ᵉ liste le justifiera | designer-ux |
| — | Fichiers du plan ARCH §7 renommés ou regroupés : pas de `JacksonConfig` (configuration YAML suffisante), pas de `CsrfCookieFilter` (le gestionnaire SPA charge le jeton), point d'entrée et gestionnaire 403 en lambdas avec `ProblemDetailWriter` ; ajout de `BusinessTime`, `FieldErrorView`, `LikePatterns` ; ECR-01/02 regroupés dans `error-pages.ts` | Comportement identique au contrat | concepteur-architecte (mise à jour du plan) |

## 7. Points d'attention pour le Reviewer et le QA
- **Sécurité** : vérifier `SecurityConfig` (refus par défaut, CSRF, 401/403), la non-révélation de l'existence d'un e-mail (temps BCrypt constant pour un e-mail inconnu) et l'absence de mots de passe dans les journaux (`toString()` des DTO masqués).
- **Cypress 16** : `Cypress.env()` est **supprimé** (utiliser `cy.env()` pour les secrets ou `Cypress.expose()`). Lancé depuis VS Code, Cypress échoue (« bad option: --smoke-test ») tant que la variable `ELECTRON_RUN_AS_NODE=1` n'est pas retirée de l'environnement.
- **Données de test** : la base est vide hormis l'administrateur initial (identifiants dans `.env`, mot de passe temporaire).
- Les captures d'écran Cypress montrent des barres de défilement : c'est un artefact de capture ; mesure faite, la page ne déborde pas (`scrollWidth = clientWidth`).

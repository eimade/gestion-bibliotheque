# ADR-004 — Sécurité : session serveur (Spring Session JDBC) plutôt que JWT

**Statut** : Accepté (17/10/2026) — **dérogation** au socle de l'agent Architecte (qui proposait JWT stateless)

## Contexte
Les SPEC exigent :
- une déconnexion après **30 minutes d'inactivité** (RG-03) avec avertissement préalable (Q-19) ;
- une déconnexion effective (AC-002-1) ;
- qu'un agent désactivé **ne puisse plus exécuter aucune action, même s'il était connecté** (AC-005-1) ;
- un mot de passe temporaire bloquant toute autre action (RG-05).
Le frontend et l'API sont servis par la **même origine** (nginx relaie `/api`, ADR-005).

Avec des JWT stateless, la révocation immédiate et l'expiration par inactivité imposent une liste de révocation ou des jetons de rafraîchissement stockés en base : on reconstruit une session, avec plus de complexité et un risque de vol de jeton accessible au JavaScript.

## Décision
1. **Session HTTP côté serveur**, persistée en base par **Spring Session JDBC** (tables `SPRING_SESSION*`, migration V1) : elle survit au redémarrage du backend.
2. Cookie de session `BIBSESSION` : `HttpOnly`, `Secure` (production), `SameSite=Strict`, chemin `/`. Durée d'inactivité **30 min** (`server.servlet.session.timeout`, RG-03).
3. **Protection CSRF** active : jeton dans le cookie `XSRF-TOKEN` (lisible par le JavaScript), renvoyé dans l'en-tête `X-XSRF-TOKEN` ; Angular le gère nativement (`withXsrfConfiguration`). Configuration « SPA » de Spring Security 6 (`CookieCsrfTokenRepository.withHttpOnlyFalse()` + gestionnaire de requête adapté).
4. **Connexion** : `POST /api/v1/auth/login` (JSON), authentification par `AuthenticationManager`, **changement d'identifiant de session** à la connexion (anti-fixation), contexte de sécurité sauvegardé dans la session. **Déconnexion** : `POST /api/v1/auth/logout` invalide la session.
5. **Nom de principal = identifiant du compte** (et non l'e-mail, qui peut changer, AC-019-1). La désactivation, l'archivage ou la réinitialisation d'un compte **suppriment toutes ses sessions** (`FindByIndexNameSessionRepository`) → effet immédiat (AC-005-1).
6. Mots de passe : `DelegatingPasswordEncoder` (BCrypt par défaut, préfixe `{bcrypt}`) ; historique des 3 derniers (RG-01) ; verrouillage après 5 échecs pendant 15 min (RG-02), message de verrouillage avec heure de fin (Q-21).
7. **Filtre « mot de passe temporaire »** (RG-05) : tant que le compte a un mot de passe temporaire, seules les routes `GET /api/v1/auth/me`, `POST /api/v1/auth/password` et `POST /api/v1/auth/logout` sont autorisées ; sinon 403 `PASSWORD_CHANGE_REQUIRED`.
8. **Autorisation** : règles par famille d'URL dans la chaîne de filtres (ARCH-socle §6) **et** `@PreAuthorize` sur les services sensibles ; contrôle d'appartenance pour `MEMBER` via `/api/v1/me/**` (ADR-003).
9. **Aucun CORS** en production (même origine). En développement, le serveur `ng serve` relaie `/api` vers le backend (même origine également).
10. Secrets uniquement par variables d'environnement ; un **administrateur initial** est créé au démarrage à partir de `APP_BOOTSTRAP_ADMIN_EMAIL` / `APP_BOOTSTRAP_ADMIN_PASSWORD` s'il n'existe aucun `ADMIN` actif (mot de passe temporaire).

## Alternatives écartées
- **JWT d'accès + jeton de rafraîchissement** : révocation immédiate impossible sans état serveur ; jeton exposé au JavaScript si stocké en `localStorage`.
- **Session en mémoire** (sans Spring Session) : perdue à chaque redémarrage ou déploiement.
- **Fournisseur d'identité externe (OIDC)** : hors périmètre (SPEC-authentification §2).

## Conséquences
- Le service est **avec état** : un seul nœud backend suffit (volumétrie d'une bibliothèque) ; le passage à plusieurs nœuds reste possible, la session étant en base.
- **HTTPS obligatoire en production** (cookie `Secure`) : terminaison TLS en amont du conteneur frontend (Q-28).
- Les tests d'API doivent gérer le cookie de session et le jeton CSRF (`spring-security-test` : `csrf()`, `with(user(...))`).

# ADR-005 — Build Gradle et déploiement Docker Compose

**Statut** : Accepté (17/10/2026) — **dérogations** au socle : Gradle Groovy DSL, Node 22, nginx non-root 1.28

## Contexte
Stack imposée : Java 21, Spring Boot 3, Angular 17+, MariaDB, Gradle, Docker et Docker Compose. La demande utilisateur nomme explicitement le fichier `build.gradle`.

## Décision
### Build backend
- **Gradle 8.14.3 avec wrapper**, script **`build.gradle` (Groovy DSL)**, à la demande explicite de l'utilisateur ; versions centralisées dans `gradle/libs.versions.toml`.
- Toolchain **Java 21**. Versions vérifiées sur Maven Central le 17/10/2026 :

| Composant | Version | Remarque |
|---|---|---|
| Spring Boot (plugin + BOM) | 3.5.16 | Dernière version 3.x (Q-29) |
| io.spring.dependency-management | 1.1.7 | |
| springdoc-openapi-starter-webmvc-ui | 2.8.17 | Ligne compatible Boot 3.5 |
| ArchUnit (junit5) | 1.5.1 | Tests d'architecture (ADR-001) |
| JaCoCo | 0.8.15 | Couverture ≥ 80 % des packages `*.service` |
| Flyway, pilote MariaDB, Spring Session, Testcontainers, JUnit, AssertJ, Mockito | gérées par le BOM Spring Boot | Aucune version fixée à la main |

- `./gradlew build` = compilation + tests (unitaires et `*IT` Testcontainers) + rapport JaCoCo + vérification de couverture.
- Jar exécutable unique `bibliotheque-backend.jar` ; `buildInfo()` pour `/actuator/info`.

### Images
| Image | Base | Points clés |
|---|---|---|
| backend | `gradle:8.14.3-jdk21` → `eclipse-temurin:21-jre-noble` | Multi-étapes, couches Spring Boot extraites (`jarmode=tools`), utilisateur `app` non-root, sonde de santé `/actuator/health/readiness` |
| frontend | `node:22-alpine` → `nginxinc/nginx-unprivileged:1.28-alpine` | Node 22 car Node 20 n'est plus maintenu depuis avril 2026 ; nginx sans privilèges (port 8080), reverse proxy `/api`, en-têtes de sécurité (CSP…) |
| db / db-backup | `mariadb:11.4` | Sonde `healthcheck.sh`, collation forcée, fuseau UTC |

### Compose (`docker-compose.yml`)
- Services `db`, `backend`, `frontend`, `db-backup`. **Seul le frontend publie un port.**
- Réseau `backend-net` **interne** (base isolée) ; `frontend-net` pour le frontend et l'accès sortant du backend (US-015, US-036).
- `depends_on` avec `condition: service_healthy` ; limites mémoire ; rotation des journaux (10 Mo × 3).
- Secrets uniquement dans `.env` (non versionné) ; `.env.example` versionné ; variables obligatoires vérifiées par `${VAR:?message}`.
- **Deux utilisateurs MariaDB** (ADR-002) : l'utilisateur applicatif est créé par `db/init/01-create-app-user.sh` à l'initialisation du volume.
- **Sauvegarde quotidienne** (`db-backup`, NFR-12) : `mariadb-dump --single-transaction` compressé, rétention configurable (14 jours par défaut), à copier hors de l'hôte.
- `docker-compose.dev.yml` : publie la base (`127.0.0.1:3306`) et le backend (`127.0.0.1:8081`) pour le développement local.

### Outillage de test fourni par le build
JUnit 5, AssertJ, Mockito, spring-security-test, **Testcontainers MariaDB** (jamais H2), ArchUnit, JaCoCo ; côté frontend, **Cypress** en devDependency avec `cypress.config.ts` (baseUrl = frontend Compose), à installer par le Développeur lors de la création du projet Angular.

## Alternatives écartées
- **Kotlin DSL (`build.gradle.kts`)** proposé par le socle : écarté au profit de la demande explicite `build.gradle`.
- **Wrapper Gradle dans l'image Docker** : l'image officielle `gradle` évite de copier `gradle-wrapper.jar` dans le contexte de build.
- **`nginx:1.27-alpine`** (socle) : s'exécute en root ; remplacé par la variante non-root.
- **Exécution des tests dans `docker build`** : Testcontainers exige un démon Docker ; les tests tournent en CI.

## Conséquences
- Vérifié le 17/10/2026 : `gradlew build` OK ; `docker compose config` OK (avec et sans surcharge dev) ; image backend construite ; `db` + `backend` démarrés et *healthy* ; migrations V1/V2 appliquées ; 35 tests de contraintes OK ; sauvegarde produite ; `nginx -t` OK.
- **Non vérifié** : construction de l'image frontend (le projet Angular n'existe pas encore). Le projet doit s'appeler `bibliotheque-frontend` (chemin de sortie `dist/bibliotheque-frontend/browser`).
- Fichiers shell en fin de ligne LF imposée par `.gitattributes`.

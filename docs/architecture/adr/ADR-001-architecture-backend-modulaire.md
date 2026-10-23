# ADR-001 — Architecture backend modulaire par domaine

**Statut** : Accepté (17/10/2026)

## Contexte
Le backend Spring Boot doit rester lisible et testable alors que 7 features (SPEC) partagent fortement leurs données : un retour crée une pénalité (RG-26, RG-36), libère un exemplaire au profit d'une réservation (RG-32), et l'éligibilité d'un adhérent dépend de son statut, de ses emprunts et de ses pénalités (RG-23). Un découpage strict « un package par feature » produirait des dépendances circulaires (emprunts ↔ réservations, adhérents ↔ pénalités).

## Décision
1. **Package racine** `com.bibliotheque`, découpé en **modules métier** (pas 1 pour 1 avec les features) :

| Module | Features couvertes | Peut dépendre de |
|---|---|---|
| `common` | transverse (config, sécurité, erreurs, pagination, audit, paramètres) | — |
| `auth` | authentification | `common` |
| `catalog` | catalogue | `common` |
| `member` | adherents | `common`, `auth` |
| `circulation` | emprunts, reservations, penalites | `common`, `catalog`, `member` |
| `reporting` | pilotage | tous (lecture seule) |

2. **Couches internes** : `web` (contrôleurs, DTO `record`, mapping manuel) → `service` (logique, `@Transactional`) → `repository` / `domain` (entités JPA, énumérations). Sous-package `job` autorisé pour les traitements planifiés (ADR-006), qui n'appellent que la couche `service`.
3. Quand un module a besoin d'une information d'un module dont il ne peut pas dépendre, il **déclare une interface** que l'autre module implémente (inversion de dépendance). Exemple : `member.service.MemberObligations` (emprunts en cours, réservations actives, montant impayé, pour RG-18) est implémentée par `circulation`.
4. Entre modules, les entités ne se référencent que par **identifiant** (`Long memberId`), jamais par association JPA traversant un module.
5. DTO = `record` + Bean Validation ; aucune entité JPA dans une réponse HTTP ; injection par constructeur ; pas de Lombok.
6. Le temps est obtenu via un bean `java.time.Clock` (`common.config`) ; les dates métier sont calculées en **Europe/Paris** (NFR-11), les instants stockés en UTC.
7. Les règles 1 à 4 sont **vérifiées automatiquement** par un test ArchUnit (`backend/src/test/java/com/bibliotheque/ArchitectureTest.java`).

## Alternatives écartées
- **Un package par feature (7 modules)** : cycles inévitables entre emprunts, réservations et pénalités.
- **Packaging par couche technique** (`controllers/`, `services/`…) : faible cohésion, couplage implicite.
- **Événements applicatifs pour découpler emprunts et réservations** : complexité transactionnelle injustifiée pour une application monolithique.
- **MapStruct** : gain faible pour des DTO simples ; une dépendance et un processeur d'annotations en moins.

## Conséquences
- Le frontend garde ses 7 features (UX) ; le backend en a 5 + `common`. La table de correspondance est dans ARCH-socle §1.
- Le module `circulation` est le plus gros ; il est structuré par sous-domaines dans ses packages (`Loan*`, `Reservation*`, `Fine*`, `Payment*`, `Eligibility*`).
- Toute nouvelle dépendance inter-modules exige une mise à jour de cet ADR et du test ArchUnit.

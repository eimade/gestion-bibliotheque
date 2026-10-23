# ADR-002 — Données : MariaDB 11.4, Flyway, intégrité en base

**Statut** : Accepté (17/10/2026)

## Contexte
Les SPEC imposent de nombreuses règles d'intégrité (unicités, états cohérents, conservation RGPD) et une exigence forte de fiabilité en cas d'accès simultanés (NFR-10 : jamais deux emprunts en cours sur un exemplaire, jamais un exemplaire attribué à deux réservations).

## Décision
1. **MariaDB 11.4 LTS**, InnoDB, `utf8mb4` / **`utf8mb4_unicode_ci`** sur chaque table. Cette collation, insensible à la casse et aux accents, applique nativement l'unicité de l'e-mail (RG-14) et du libellé de catégorie (RG-12) ainsi que la recherche sans accents (RG-11).
2. **Schéma géré exclusivement par Flyway** (`backend/src/main/resources/db/migration`, `V<n>__<description>.sql`), `ddl-auto=validate`. Une migration appliquée est **immuable**.
3. **Conventions** : tables `snake_case` au pluriel ; PK `id BIGINT AUTO_INCREMENT` ; `created_at`, `updated_at` (`DATETIME(6)`, UTC) ; `version INT` (verrou optimiste JPA) sur toute table modifiable ; contraintes nommées `fk_`, `uk_`, `ck_`, `idx_` + table + colonnes ; énumérations en `VARCHAR` avec `CHECK` sur les valeurs ; montants `DECIMAL(10,2)` ; dates métier en `DATE`.
4. **Toute règle exprimable sans la date du jour ni les paramètres l'est en base** (`CHECK`, `UNIQUE`, `FK`), en plus de la validation applicative.
5. **Unicités partielles par colonnes générées** `PERSISTENT` valant `NULL` hors état actif, portant un index `UNIQUE` :
   - `loans.active_copy_id` → un seul emprunt `ACTIVE`/`OVERDUE` par exemplaire (NFR-10) ;
   - `reservations.active_member_id` + `book_id` → une réservation active par adhérent et par notice (RG-31) ;
   - `reservations.ready_copy_id` → un exemplaire mis de côté pour une seule réservation (RG-32).
   Ces colonnes ne sont **pas mappées** dans les entités JPA.
6. **Numéros fonctionnels** par séquences MariaDB (`member_number_seq`, `copy_number_seq`) : jamais réattribués (RG-09, RG-13).
7. **RGPD par effacement de colonnes** : l'archivage met à `NULL` les colonnes d'identité (`members`) et l'e-mail du compte ; l'anonymisation d'un emprunt rendu met `loans.member_id` à `NULL`. Des `CHECK` interdisent ces `NULL` hors des états prévus.
8. **Deux utilisateurs** : `DB_MIGRATION_USER` (propriétaire du schéma, Flyway, sauvegardes) et `DB_APP_USER` (`SELECT`, `INSERT`, `UPDATE`, `DELETE` uniquement).
9. **Recherche catalogue** par `LIKE` sur colonnes indexées en collation insensible aux accents. Volume cible : 50 000 notices (NFR-01). Un index `FULLTEXT` ne sera introduit (nouvelle migration) que si une mesure dépasse NFR-01 : sa longueur minimale de mot (3) est incompatible avec RG-11 (2 caractères).

## Alternatives écartées
- **`utf8mb4_uca1400_ai_ci`** (défaut de MariaDB 11) : équivalent fonctionnel, mais `unicode_ci` est imposé par le socle et largement supporté par les outils.
- **Unicité contrôlée seulement par le service** : non sûre en concurrence (NFR-10).
- **Hibernate `ddl-auto=update`** : schéma non maîtrisé, pas de `CHECK`, pas d'historique.
- **H2 pour les tests** : comportements différents (collations, colonnes générées, séquences) ; Testcontainers MariaDB à la place (ADR-005).

## Conséquences
- Une violation de contrainte se traduit en code d'erreur métier par le gestionnaire d'exceptions (ARCH-socle §4) ; le service vérifie d'abord la règle pour produire un message précis, la contrainte reste le filet de sécurité.
- Avertissement connu : la version de Flyway gérée par Spring Boot 3.5.16 indique ne pas avoir testé MariaDB 11.4 (dernière testée : 11.2). Les migrations V1 et V2 ont été appliquées avec succès sur MariaDB 11.4 le 17/10/2026 ; à revérifier à chaque montée de version.
- Les noms de clés primaires ne sont pas déclarés (MariaDB les ignore et émet l'avertissement 1280).

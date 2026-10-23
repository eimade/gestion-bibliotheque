---
feature: pilotage
agent: concepteur-architecte
version: 1
statut: READY
sources: [docs/specs/SPEC-pilotage.md, docs/design/UX-pilotage.md, docs/architecture/ARCH-socle.md, docs/architecture/ARCH-circulation.md, docs/architecture/adr/ADR-001-architecture-backend-modulaire.md]
---
# ARCH — Pilotage : tableau de bord, statistiques, paramètres, audit

## 1. Vue d'ensemble
Le module backend est `com.bibliotheque.reporting`, en lecture seule sur toutes les tables (ADR-001). Une seule exception : la modification des paramètres, qui écrit dans `settings` via `common.settings`.

Côté frontend, la feature est `features/pilotage` (ECR-37 → ECR-40). Le tableau de bord devient la page d'accueil du personnel (`/gestion`), ce qui remplace la page d'attente (Q-33).

Les lectures agrégées utilisent `JdbcClient` avec des requêtes SQL paramétrées : agrégats mensuels, comptages du jour. Aucune concaténation n'est faite à partir d'une saisie.

## 2. Modèle de données
Aucune migration. Tables lues : `loans`, `reservations`, `fines`, `payments`, `members`, `copies`, `books`, `book_categories`, `categories`, `settings` et `audit_entries`.

## 3. Contrat d'API (extrait de openapi.yaml)
| Méthode | Chemin | Rôles | Requête | Réponse | Codes | US |
|---|---|---|---|---|---|---|
| GET | `/dashboard` | LIBRARIAN, ADMIN | — | 200 `Dashboard` | — | US-041 |
| GET | `/statistics` | ADMIN | `month` (`AAAA-MM`) | 200 `MonthlyStatistics` | 400, 422 `MONTH_IN_FUTURE` | US-042 |
| GET | `/statistics/export` | ADMIN | `month` | 200 `text/csv` (UTF-8 avec BOM, séparateur `;`) | 400, 422 `MONTH_IN_FUTURE` | US-042 (AC-042-3) |
| GET | `/settings` | ADMIN | — | 200 `SettingView[]` | — | US-043 |
| PUT | `/settings/{code}` | ADMIN | `SettingUpdateRequest` (`value`, `version`) | 200 `SettingView` | 400, 404, 409 `CONCURRENT_MODIFICATION`, 422 `SETTING_OUT_OF_RANGE` (+`min`, `max`) | US-043 |
| GET | `/audit-entries` | ADMIN | `object`, `actor`, `action`, `from`, `to`, `page`, `size` | 200 `PageResponse<AuditEntryView>` | 400 | US-044 |

**DTO** :
- `Dashboard` : `date`, `loansToday`, `returnsToday`, `overdue`, `readyReservations`, `readyLastDay`, `toShelve`, `computedAt`.
- `MonthlyStatistics` :
  - compteurs : `month`, `loans`, `returns`, `renewals`, `reservations`, `newMembers`, `activeBorrowers` ;
  - `topBooks[]` : `bookId`, `title`, `authors`, `loans` ;
  - `rotation[]` : `category`, `loans`, `copies`, `rate` ;
  - `collections` : `cash`, `card`, `check`, `waived`.
- `SettingView` : `code`, `group`, `valueType`, `unit`, `value`, `min`, `max`, `updatedAt`, `updatedBy`, `version`.
- `AuditEntryView` : `id`, `occurredAt`, `actorLabel`, `action`, `objectType`, `objectRef`, `details` (objet JSON).

## 4. Règles de gestion → application
- **RG-40** : bornes vérifiées par le service (`SETTING_OUT_OF_RANGE`) et en base (`ck_settings_range`, `ck_settings_integer`). La modification est journalisée avec l'ancienne et la nouvelle valeur. Elle n'est jamais rétroactive : les échéances et les montants sont figés à leur création.
- **RG-41** : mois civil en heure de Paris.
  - emprunts : `loan_date` dans le mois ;
  - retours : `return_date` dans le mois ;
  - prolongations : entrées d'audit `LOAN` avec `details.event = "RENEWAL"` dans le mois (Q-43) ;
  - réservations : `reserved_at` ;
  - nouveaux adhérents : `registration_date` ;
  - adhérents ayant emprunté : `member_id` distincts parmi les emprunts du mois (Q-44) ;
  - rotation : emprunts du mois des notices de la catégorie ÷ exemplaires de la catégorie hors `WITHDRAWN` et `LOST`, arrondi à 2 décimales ; 0 si la catégorie n'a aucun exemplaire ;
  - encaissements : `payments.collected_at` dans le mois, par mode ;
  - remises : pénalités `WAIVED` réglées dans le mois.
- **Tableau de bord** (US-041) : il est calculé à la date du jour.
  - Les emprunts en retard ont le statut effectif `OVERDUE` (ADR-006 §3).
  - « À remettre en rayon » couvre le même périmètre qu'ECR-33 : réservations `EXPIRED` ou `CANCELLED` qui étaient `READY`, closes depuis le début de la veille (Q-26).

## 5. Sécurité
- `/api/v1/dashboard` : LIBRARIAN, ADMIN.
- `/api/v1/statistics/**`, `/settings/**`, `/audit-entries/**` : ADMIN.
- Aucune méthode d'écriture ni de suppression n'est exposée sur le journal d'audit (AC-044-3).
- Les statistiques et l'export ne contiennent aucune donnée nominative (AC-042-3).

## 6. Plan d'implémentation
1. `common.settings` : `Setting.update(value, actorId, now)`, exposition des champs.
2. `reporting.repository` : `DashboardQueries`, `StatisticsQueries`, `AuditQueries` (`JdbcClient`).
3. `reporting.service` : `DashboardService`, `StatisticsService` (+ export CSV), `SettingsAdminService`, `AuditLogService`.
4. `reporting.web` : `DashboardController`, `StatisticsController`, `SettingsController`, `AuditController`.
5. Frontend `features/pilotage` : ECR-37 → ECR-40.

## 7. Questions ouvertes
| ID | Question | Hypothèse retenue | Impact si faux |
|---|---|---|---|
| Q-43 | Le schéma ne garde pas la date de chaque prolongation | Comptage par le journal d'audit (événement `RENEWAL`), conservé 12 mois (NFR-08) | Table d'historique des prolongations (migration) pour des statistiques au-delà de 12 mois |
| Q-44 | Après anonymisation (RG-19, 4 mois), l'emprunteur d'un emprunt rendu n'est plus connu | « Adhérents ayant emprunté » est exact pour les 4 derniers mois, puis sous-estimé | Compteur mensuel figé à la clôture du mois |
| Q-45 | Filtre « Auteur » du journal : l'UX prévoit une autocomplétion sur les comptes | Filtre texte sur le libellé d'auteur (contient) | Ajout d'un point de recherche des comptes pour l'autocomplétion |

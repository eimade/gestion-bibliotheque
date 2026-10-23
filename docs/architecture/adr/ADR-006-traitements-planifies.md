# ADR-006 — Traitements planifiés

**Statut** : Accepté (17/10/2026)

## Contexte
Plusieurs règles se déclenchent au passage d'une date, sans action d'un utilisateur : retards (RG-27), expiration des réservations (RG-33, au plus tard 00 h 15 selon NFR-11), expiration des adhésions (RG-16), anonymisation des emprunts (RG-19), purge du journal d'audit (NFR-08).

## Décision
1. **`@Scheduled` de Spring** (pool de 2 threads), expressions cron et fuseau **Europe/Paris** externalisés (`app.jobs.*`) :

| Traitement | Module / package | Cron (heure de Paris) | Règle |
|---|---|---|---|
| Passage `ACTIVE` → `OVERDUE` des emprunts échus | `circulation.job` | `0 0 0 * * *` | RG-27 |
| Expiration des réservations `READY` non retirées + réattribution | `circulation.job` | `0 5 0 * * *` | RG-33, RG-32 |
| Passage `ACTIVE` → `EXPIRED` des adhésions échues | `member.job` | `0 10 0 * * *` | RG-16 |
| Effacement de l'adhérent des emprunts rendus depuis 4 mois sans pénalité impayée | `circulation.job` | `0 0 2 * * *` | RG-19 |
| Purge du journal d'audit de plus de 12 mois | `common.audit` | `0 0 3 * * *` | NFR-08 |

2. Chaque traitement est **idempotent**, traite par **lots de 500** dans des transactions courtes, et journalise un résumé (nombre d'éléments traités) avec l'auteur « Système » (NFR-08).
3. **Exactitude indépendante de l'horaire** : les lectures n'attendent pas le traitement. Un emprunt dont `due_date` est passée est **présenté et traité comme en retard** même si le traitement de minuit n'a pas encore tourné (éligibilité RG-23, liste US-028). De même, une réservation `READY` dont la date limite est passée est considérée expirée lors d'un emprunt.
4. **Une seule instance backend** (ADR-004) : pas de verrou distribué.

## Alternatives écartées
- **Événements planifiés MariaDB (`EVENT`)** : logique métier dupliquée en SQL, journalisation d'audit impossible.
- **Ordonnanceur externe (cron hôte, Quartz)** : complexité injustifiée.
- **ShedLock** : inutile avec une seule instance ; à ajouter si le backend est répliqué.

## Conséquences
- Les tests utilisent une `Clock` fixe pour simuler le passage des dates (QA : jamais de `Thread.sleep`).
- Le passage à plusieurs instances backend imposera un verrou distribué (nouvel ADR).

---
feature: pilotage
agent: spec-kit-ba
version: 1
statut: READY
sources: [besoin utilisateur (conversation du 2026-10-17), .claude/agents/1-spec-kit-ba.md, docs/specs/INDEX.md, docs/specs/SPEC-adherents.md, docs/specs/SPEC-emprunts.md, docs/specs/SPEC-reservations.md, docs/specs/SPEC-penalites.md]
---
# SPEC — Pilotage : tableau de bord, statistiques, paramétrage et audit

## 1. Objectif métier
Donner au personnel une vue immédiate du travail du jour, à la direction des indicateurs fiables pour orienter les acquisitions, et à l'administrateur la maîtrise des règles de prêt sans intervention technique, le tout avec une traçabilité consultable.
**Indicateur de succès** : le rapport d'activité annuel est produit à partir de l'application sans comptage manuel ; une modification des règles de prêt est appliquée en ≤ 5 minutes par l'administrateur.

## 2. Périmètre
- **Inclus** : tableau de bord quotidien du guichet, statistiques mensuelles et export, paramétrage des valeurs des règles de gestion, consultation du journal d'audit.
- **Exclus** : rapports personnalisables ; statistiques nominatives par adhérent ; tableaux de bord publics ; envoi automatique de rapports.

## 3. Acteurs et rôles
| Acteur | Rôle applicatif | Description |
|---|---|---|
| Bibliothécaire | `LIBRARIAN` | Consulte le tableau de bord du jour. |
| Administrateur | `ADMIN` | Consulte le tableau de bord, les statistiques et le journal d'audit ; modifie les paramètres. |

## 4. Entités métier
### ENT-Parametre (`Setting`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Code | Règle concernée (ex. « durée d'emprunt ») | Oui | Liste fermée [RG-40] |
| Valeur | Valeur en vigueur | Oui | Bornes [RG-40] |
| Date de dernière modification, auteur | — | Oui | Automatique |

**Cycle de vie** : N/A — un paramètre existe toujours avec sa valeur par défaut et n'a pas d'état ; l'historique des valeurs est conservé dans le journal d'audit (NFR-08).

### ENT-JournalAudit (`AuditEntry`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Date-heure | Moment de l'action | Oui | Automatique |
| Auteur | Compte ayant agi, ou « Système » | Oui | — |
| Action | Ex. création, modification, changement d'état | Oui | — |
| Objet | Type et identifiant fonctionnel de l'objet (ex. emprunt, adhérent A000123) | Oui | — |
| Valeurs avant / après | Champs modifiés | Si modification | Jamais de mot de passe |

**Cycle de vie** : N/A — une entrée est créée une fois, jamais modifiée ni supprimée avant la fin de sa durée de conservation (NFR-08 : 12 mois).

## 5. Règles de gestion
| ID | Règle | Valeur | Source/Hypothèse |
|---|---|---|---|
| RG-40 | Paramètres modifiables par l'`ADMIN`, avec leurs bornes : durée d'adhésion [RG-15] (1-36 mois) et délai de renouvellement anticipé (0-90 jours) ; durée d'emprunt [RG-21] (1-90 jours) ; quota d'emprunts [RG-22] (1-50) ; nombre (0-5) et durée (1-90 jours) de prolongation [RG-25] ; quota de réservations [RG-30] (0-20) ; délai de retrait [RG-33] (1-15 jours) ; seuil de blocage [RG-35] (0,00-100,00 €) ; tarif journalier (0,00-5,00 €) et plafond (0,00-100,00 €) de retard [RG-36] ; forfait de perte [RG-37] (0,00-500,00 €). Une nouvelle valeur s'applique uniquement aux opérations postérieures à la modification (pas de recalcul des échéances ni des pénalités existantes) | Bornes indiquées | Hypothèse Q-18 |
| RG-41 | Statistiques calculées par mois civil, incluant les données anonymisées [RG-19] ; taux de rotation d'une catégorie = nombre d'emprunts du mois ÷ nombre d'exemplaires de la catégorie hors `WITHDRAWN` et `LOST` au dernier jour du mois, arrondi à 2 décimales | Mois civil | Règle métier |

## 6. User stories

### US-041 — Consulter le tableau de bord du jour · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** voir en ouvrant l'application les chiffres et tâches du jour, **afin d'**organiser mon travail au guichet.
**Critères d'acceptation**
```gherkin
# AC-041-1 — nominal
Scénario: Indicateurs du jour
  Étant donné, à la date du jour, 12 emprunts et 8 retours enregistrés, 5 emprunts "OVERDUE", 3 réservations "READY" dont 1 dont la date limite de retrait est aujourd'hui, et 2 réservations expirées à remettre en rayon
  Quand le bibliothécaire ouvre le tableau de bord
  Alors il voit : emprunts du jour 12, retours du jour 8, retards en cours 5, réservations prêtes 3, dont 1 à échéance aujourd'hui, exemplaires à remettre en rayon 2
  Et chaque indicateur donne accès à la liste détaillée correspondante (US-028, US-035)

# AC-041-2 — vide
Scénario: Journée sans activité
  Étant donné aucune opération à la date du jour
  Quand le bibliothécaire ouvre le tableau de bord
  Alors tous les indicateurs affichent 0

# AC-041-3 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente d'ouvrir le tableau de bord du personnel
  Alors l'action est refusée
```

### US-042 — Consulter les statistiques d'activité · Priorité : COULD
**En tant qu'**administrateur, **je veux** consulter et exporter les statistiques mensuelles, **afin de** rendre compte de l'activité et orienter les acquisitions.
**Critères d'acceptation**
```gherkin
# AC-042-1 — nominal
Scénario: Statistiques d'un mois
  Étant donné l'activité d'octobre 2026
  Quand l'administrateur sélectionne le mois d'octobre 2026
  Alors il voit : nombre d'emprunts, de retours, de prolongations, de réservations, de nouveaux adhérents, d'adhérents ayant emprunté au moins 1 fois
  Et les 10 notices les plus empruntées
  Et le taux de rotation par catégorie [RG-41]
  Et le montant encaissé par mode de paiement et le montant des remises accordées

# AC-042-2 — nominal
Scénario: Données anonymisées incluses
  Étant donné des emprunts d'octobre 2026 dont l'emprunteur a été anonymisé [RG-19]
  Quand l'administrateur consulte octobre 2026
  Alors ces emprunts sont comptés [RG-41]

# AC-042-3 — nominal
Scénario: Export
  Quand l'administrateur exporte les statistiques du mois
  Alors il obtient un fichier tableur ouvrable dans un tableur courant, contenant les mêmes valeurs qu'à l'écran et aucune donnée nominative

# AC-042-4 — erreur
Scénario: Mois futur
  Quand l'administrateur sélectionne un mois postérieur au mois en cours
  Alors la sélection est refusée

# AC-042-5 — droits
Scénario: Bibliothécaire non autorisé
  Quand un utilisateur "LIBRARIAN" tente de consulter les statistiques
  Alors l'action est refusée
```

### US-043 — Paramétrer les règles de prêt · Priorité : SHOULD
**En tant qu'**administrateur, **je veux** modifier les valeurs des règles de prêt, **afin d'**adapter le fonctionnement aux décisions de la bibliothèque sans intervention technique.
**Critères d'acceptation**
```gherkin
# AC-043-1 — nominal
Scénario: Modification de la durée d'emprunt
  Étant donné la durée d'emprunt [RG-21] à 21 jours et un emprunt existant à rendre le 07/11/2026
  Quand l'administrateur la passe à 28 jours
  Alors un emprunt enregistré le 17/10/2026 après la modification a pour date de retour prévue le 14/11/2026
  Et l'emprunt existant reste à rendre le 07/11/2026 [RG-40]
  Et la modification est journalisée avec l'ancienne et la nouvelle valeur

# AC-043-2 — nominal
Scénario: Consultation des paramètres
  Quand l'administrateur ouvre le paramétrage
  Alors il voit chaque paramètre de [RG-40] avec sa valeur, ses bornes, la date et l'auteur de sa dernière modification

# AC-043-3 — limite
Scénario: Valeur hors bornes
  Quand l'administrateur saisit un quota d'emprunts de 0 ou de 51 [RG-40]
  Alors l'enregistrement est refusé et les bornes sont indiquées

# AC-043-4 — limite
Scénario: Abaissement du quota sous le nombre d'emprunts d'un adhérent
  Étant donné un adhérent ayant 5 emprunts en cours
  Quand l'administrateur abaisse le quota [RG-22] à 3
  Alors les 5 emprunts restent valides
  Et l'adhérent ne peut plus emprunter tant qu'il a 3 emprunts en cours ou plus [RG-23]

# AC-043-5 — droits
Scénario: Bibliothécaire non autorisé
  Quand un utilisateur "LIBRARIAN" tente de modifier un paramètre
  Alors l'action est refusée
```

### US-044 — Consulter le journal d'audit · Priorité : COULD
**En tant qu'**administrateur, **je veux** consulter l'historique des actions, **afin de** répondre à une contestation ou détecter une anomalie.
**Critères d'acceptation**
```gherkin
# AC-044-1 — nominal
Scénario: Recherche dans le journal
  Étant donné des actions journalisées sur l'adhérent A000123
  Quand l'administrateur filtre le journal sur l'objet "A000123" et une période
  Alors il voit les actions du plus récent au plus ancien avec date-heure, auteur, action et valeurs avant/après

# AC-044-2 — sécurité
Scénario: Aucun secret dans le journal
  Étant donné un changement de mot de passe journalisé
  Quand l'administrateur consulte l'entrée
  Alors aucun mot de passe, ancien ou nouveau, n'apparaît

# AC-044-3 — erreur
Scénario: Journal non modifiable
  Quand un utilisateur, quel que soit son rôle, tente de modifier ou supprimer une entrée du journal
  Alors l'action est refusée

# AC-044-4 — droits
Scénario: Bibliothécaire non autorisé
  Quand un utilisateur "LIBRARIAN" tente de consulter le journal d'audit
  Alors l'action est refusée
```

## 7. Exigences non fonctionnelles
| ID | Catégorie | Exigence vérifiable |
|---|---|---|
| NFR-08 | Audit | Journal conservé 12 mois, non modifiable (AC-044-3). |
| NFR-07 | RGPD | Statistiques et exports sans aucune donnée nominative (AC-042-3). |
| NFR-01 | Performance | Tableau de bord affiché en ≤ 2 s ; statistiques d'un mois en ≤ 5 s. |

## 8. Matrice de traçabilité
| US | AC | RG | Priorité |
|---|---|---|---|
| US-041 | AC-041-1 → AC-041-3 | RG-33 | SHOULD |
| US-042 | AC-042-1 → AC-042-5 | RG-19, RG-41 | COULD |
| US-043 | AC-043-1 → AC-043-5 | RG-21, RG-22, RG-23, RG-40 | SHOULD |
| US-044 | AC-044-1 → AC-044-4 | — | COULD |

## 9. Questions ouvertes
| ID | Question | Hypothèse retenue | Impact si faux |
|---|---|---|---|
| Q-18 | Les règles de prêt doivent-elles être modifiables par l'administrateur, et avec quel effet sur l'existant ? | Oui, dans les bornes de [RG-40], sans effet rétroactif | Sans paramétrage : valeurs figées, US-043 supprimée ; avec rétroactivité : recalcul des échéances en cours |

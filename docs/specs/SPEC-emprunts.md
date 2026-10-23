---
feature: emprunts
agent: spec-kit-ba
version: 1
statut: READY
sources: [besoin utilisateur (conversation du 2026-10-17), .claude/agents/1-spec-kit-ba.md, docs/specs/INDEX.md, docs/specs/SPEC-catalogue.md, docs/specs/SPEC-adherents.md, docs/specs/SPEC-penalites.md, docs/specs/SPEC-reservations.md]
---
# SPEC — Emprunts, retours et prolongations

## 1. Objectif métier
Enregistrer de façon fiable et rapide chaque sortie et chaque retour de document, appliquer automatiquement les règles de prêt (quotas, durées, blocages) et rendre visibles les retards, afin que les documents circulent équitablement entre les adhérents.
**Indicateur de succès** : emprunt ou retour enregistré au guichet en ≤ 30 s ; 0 exemplaire au statut incohérent (ex. « disponible » alors qu'il est emprunté) lors de l'inventaire annuel.

## 2. Périmètre
- **Inclus** : enregistrement d'un emprunt, d'un retour, prolongation (par l'adhérent ou au guichet), passage automatique en retard, liste des retards, déclaration de perte, exemplaire retrouvé, historique de circulation d'un exemplaire.
- **Exclus** : prêt en libre-service par automate ; relances par e-mail, SMS ou courrier [Q-12] ; prêt entre bibliothèques ; calendrier des jours de fermeture [Q-11] ; calcul et encaissement des pénalités (SPEC-penalites, seul le déclenchement est décrit ici).

## 3. Acteurs et rôles
| Acteur | Rôle applicatif | Description |
|---|---|---|
| Bibliothécaire | `LIBRARIAN` | Enregistre emprunts, retours, prolongations, pertes ; suit les retards. |
| Administrateur | `ADMIN` | Mêmes droits que le bibliothécaire. |
| Adhérent | `MEMBER` | Prolonge ses propres emprunts depuis son espace. |
| Système | — | Fait passer les emprunts en retard à l'échéance [RG-27]. |

## 4. Entités métier
### ENT-Emprunt (`Loan`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Exemplaire | ENT-Exemplaire emprunté | Oui | [RG-24] |
| Adhérent | ENT-Adherent emprunteur | Oui (effacé après [RG-19]) | [RG-23] |
| Date d'emprunt | Date d'enregistrement de la sortie | Oui | Date du jour, non modifiable |
| Date de retour prévue | Échéance | Oui | [RG-21] ; recalculée à la prolongation [RG-25] |
| Nombre de prolongations | — | Oui | 0 à la création ; plafond [RG-25] |
| Date de retour effective | Date d'enregistrement du retour | Si `RETURNED` | [RG-26] |
| Date de déclaration de perte | — | Si `LOST` | [RG-28] |
| Agent ayant enregistré l'emprunt / le retour | Compte du personnel | Oui / si `RETURNED` | Automatique |

**Cycle de vie** : création → `ACTIVE` · `ACTIVE` → `OVERDUE` (lendemain de la date de retour prévue [RG-27]) · `ACTIVE`/`OVERDUE` → `RETURNED` (retour, US-026) · `ACTIVE`/`OVERDUE` → `LOST` (perte déclarée, US-029). `RETURNED` et `LOST` sont des états finaux.
**Emprunts « en cours »** = statuts `ACTIVE` et `OVERDUE`.

## 5. Règles de gestion
| ID | Règle | Valeur | Source/Hypothèse |
|---|---|---|---|
| RG-21 | Durée d'un emprunt en jours calendaires : date de retour prévue = date d'emprunt + durée | 21 jours | Valeur par défaut du domaine, Q-09 |
| RG-22 | Nombre maximal d'emprunts en cours simultanés par adhérent | 5 | Valeur par défaut du domaine, Q-09 |
| RG-23 | Un adhérent peut emprunter seulement si toutes les conditions sont réunies : statut `ACTIVE` [RG-16] ; aucun emprunt `OVERDUE` ; total des pénalités `UNPAID` ≤ seuil [RG-35] ; nombre d'emprunts en cours < [RG-22]. En cas de refus, tous les motifs non respectés sont indiqués | — | Règle métier |
| RG-24 | Un exemplaire est empruntable s'il est `AVAILABLE`, ou `RESERVED` pour une réservation `READY` de ce même adhérent (la réservation passe alors `FULFILLED`). Tout autre statut ou une mise de côté pour un autre adhérent entraîne un refus | — | Règle métier |
| RG-25 | Prolongation : au plus N par emprunt ; nouvelle date de retour prévue = date de retour prévue actuelle + durée P ; refusée si l'emprunt est `OVERDUE`, si au moins 1 réservation `PENDING` existe sur la notice, ou si l'adhérent ne remplit pas les conditions de [RG-23] hors quota | N = 1 ; P = 21 jours | Valeur par défaut du domaine, Q-09 |
| RG-26 | Retour : date de retour effective = date du jour ; possible quel que soit le statut de l'adhérent. L'exemplaire passe `RESERVED` (et la première réservation `PENDING` de la file [RG-32] passe `READY`) s'il existe une réservation `PENDING` sur la notice, sinon `AVAILABLE`. Un retour après la date de retour prévue crée une pénalité de retard [RG-36] | — | Règle métier |
| RG-27 | Un emprunt `ACTIVE` passe `OVERDUE` le lendemain de sa date de retour prévue. Jours de retard = date de retour effective (ou date du jour) − date de retour prévue | Bascule à 00 h 00, heure de Paris | Règle métier ; NFR-11 |
| RG-28 | Perte : déclarée par le personnel sur un emprunt en cours ; l'emprunt passe `LOST`, l'exemplaire `LOST` ; une pénalité de retard (jours de retard à la date de déclaration [RG-36]) et une pénalité forfaitaire de perte [RG-37] sont créées. Un exemplaire `LOST` retrouvé redevient `AVAILABLE` (ou `RESERVED` selon [RG-26]) sans annulation automatique des pénalités | — | Hypothèse Q-10 |

## 6. User stories

### US-025 — Enregistrer un emprunt · Priorité : MUST
**En tant que** bibliothécaire, **je veux** enregistrer la sortie d'un exemplaire au nom d'un adhérent, **afin de** savoir à tout moment qui détient quel document et jusqu'à quand.
**Critères d'acceptation**
```gherkin
# AC-025-1 — nominal
Scénario: Emprunt d'un exemplaire disponible
  Étant donné la date du jour 17/10/2026
  Et un adhérent "ACTIVE" ayant 2 emprunts en cours, aucun retard et aucune pénalité
  Et un exemplaire "AVAILABLE"
  Quand le bibliothécaire identifie l'adhérent puis l'exemplaire et valide
  Alors un emprunt "ACTIVE" est créé avec la date de retour prévue 07/11/2026 [RG-21]
  Et l'exemplaire passe "ON_LOAN"
  Et le bibliothécaire voit la date de retour prévue et le nombre d'emprunts en cours (3)

# AC-025-2 — nominal
Scénario: Emprunt d'un exemplaire mis de côté pour cet adhérent
  Étant donné un exemplaire "RESERVED" pour la réservation "READY" de l'adhérent A000123
  Quand le bibliothécaire enregistre l'emprunt de cet exemplaire par A000123
  Alors l'emprunt est créé, l'exemplaire passe "ON_LOAN" et la réservation passe "FULFILLED" [RG-24]

# AC-025-3 — limite
Scénario: Quota atteint
  Étant donné un adhérent ayant exactement le nombre maximal d'emprunts en cours [RG-22]
  Quand le bibliothécaire tente un nouvel emprunt
  Alors l'emprunt est refusé avec le motif « nombre maximal d'emprunts atteint » [RG-23]

# AC-025-4 — limite
Scénario: Dernier emprunt autorisé
  Étant donné un adhérent ayant le nombre maximal d'emprunts [RG-22] moins 1
  Quand le bibliothécaire enregistre un emprunt
  Alors l'emprunt est accepté

# AC-025-5 — erreur
Scénario: Adhérent non éligible, motifs multiples
  Étant donné un adhérent "ACTIVE" ayant 1 emprunt "OVERDUE" et un total de pénalités "UNPAID" supérieur au seuil [RG-35]
  Quand le bibliothécaire tente un emprunt
  Alors l'emprunt est refusé et les 2 motifs sont indiqués : « emprunt en retard » et « pénalités impayées » [RG-23]

# AC-025-6 — limite
Scénario: Pénalités exactement au seuil
  Étant donné un adhérent dont le total des pénalités "UNPAID" est exactement égal au seuil [RG-35]
  Quand le bibliothécaire enregistre un emprunt
  Alors l'emprunt est accepté

# AC-025-7 — erreur
Scénario: Adhérent expiré ou suspendu
  Étant donné un adhérent "EXPIRED" ou "SUSPENDED"
  Quand le bibliothécaire tente un emprunt
  Alors l'emprunt est refusé avec le motif correspondant au statut [RG-16]

# AC-025-8 — erreur
Scénario: Exemplaire mis de côté pour un autre adhérent
  Étant donné un exemplaire "RESERVED" pour un autre adhérent
  Quand le bibliothécaire tente de le prêter
  Alors l'emprunt est refusé avec le motif « exemplaire réservé pour un autre adhérent » [RG-24]

# AC-025-9 — erreur
Scénario: Exemplaire non empruntable
  Étant donné un exemplaire "ON_LOAN", "LOST" ou "WITHDRAWN", ou un numéro d'exemplaire inconnu
  Quand le bibliothécaire tente de le prêter
  Alors l'emprunt est refusé avec le statut ou le motif « exemplaire inconnu »

# AC-025-10 — concurrence
Scénario: Deux guichets prêtent le même exemplaire en même temps
  Étant donné un exemplaire "AVAILABLE"
  Quand deux bibliothécaires valident simultanément son emprunt pour deux adhérents différents
  Alors un seul emprunt est créé et l'autre est refusé avec le motif « exemplaire déjà emprunté » [NFR-10]

# AC-025-11 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente d'enregistrer un emprunt
  Alors l'action est refusée
```

### US-026 — Enregistrer un retour · Priorité : MUST
**En tant que** bibliothécaire, **je veux** enregistrer le retour d'un exemplaire en le scannant, **afin de** libérer l'adhérent et remettre le document en circulation.
**Critères d'acceptation**
```gherkin
# AC-026-1 — nominal
Scénario: Retour à l'heure sans réservation
  Étant donné un emprunt "ACTIVE" de date de retour prévue 07/11/2026 et aucune réservation sur la notice
  Quand le bibliothécaire enregistre le retour le 05/11/2026
  Alors l'emprunt passe "RETURNED" avec la date de retour effective 05/11/2026 [RG-26]
  Et l'exemplaire passe "AVAILABLE"
  Et aucune pénalité n'est créée

# AC-026-2 — nominal
Scénario: Retour avec réservation en attente
  Étant donné un emprunt en cours et 2 réservations "PENDING" sur la notice
  Quand le bibliothécaire enregistre le retour
  Alors l'exemplaire passe "RESERVED"
  Et la réservation la plus ancienne passe "READY" avec sa date limite de retrait [RG-32] [RG-33]
  Et le bibliothécaire est invité à mettre l'exemplaire de côté au nom et numéro de cet adhérent

# AC-026-3 — nominal
Scénario: Retour en retard
  Étant donné un emprunt "OVERDUE" de date de retour prévue 07/11/2026
  Quand le bibliothécaire enregistre le retour le 12/11/2026
  Alors l'emprunt passe "RETURNED"
  Et une pénalité de retard "UNPAID" pour 5 jours est créée selon [RG-36]
  Et son montant est affiché au bibliothécaire

# AC-026-4 — limite
Scénario: Retour le jour de l'échéance
  Étant donné un emprunt de date de retour prévue 07/11/2026
  Quand le retour est enregistré le 07/11/2026
  Alors aucune pénalité n'est créée [RG-27]

# AC-026-5 — nominal
Scénario: Retour par un adhérent expiré
  Étant donné un emprunt en cours d'un adhérent "EXPIRED"
  Quand le bibliothécaire enregistre le retour
  Alors le retour est accepté [RG-26]

# AC-026-6 — erreur
Scénario: Exemplaire non emprunté
  Étant donné un exemplaire "AVAILABLE"
  Quand le bibliothécaire tente d'enregistrer son retour
  Alors l'opération est refusée avec le motif « aucun emprunt en cours pour cet exemplaire »

# AC-026-7 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente d'enregistrer un retour
  Alors l'action est refusée
```

### US-027 — Prolonger un emprunt · Priorité : MUST
**En tant qu'**adhérent, **je veux** prolonger un emprunt depuis mon espace, **afin de** garder le document plus longtemps sans me déplacer.
**Critères d'acceptation**
```gherkin
# AC-027-1 — nominal
Scénario: Prolongation acceptée
  Étant donné un adhérent "ACTIVE" sans pénalité au-delà du seuil [RG-35]
  Et son emprunt "ACTIVE" de date de retour prévue 07/11/2026, jamais prolongé, sans réservation sur la notice
  Quand il demande la prolongation
  Alors la date de retour prévue devient 28/11/2026 [RG-25]
  Et le nombre de prolongations passe à 1

# AC-027-2 — limite
Scénario: Plafond de prolongations atteint
  Étant donné un emprunt déjà prolongé le nombre maximal de fois [RG-25]
  Quand l'adhérent demande une nouvelle prolongation
  Alors elle est refusée avec le motif « nombre maximal de prolongations atteint »

# AC-027-3 — erreur
Scénario: Livre réservé par un autre adhérent
  Étant donné une réservation "PENDING" sur la notice de l'emprunt
  Quand l'adhérent demande la prolongation
  Alors elle est refusée avec le motif « document réservé par un autre adhérent » [RG-25]

# AC-027-4 — erreur
Scénario: Emprunt en retard
  Étant donné un emprunt "OVERDUE"
  Quand l'adhérent demande la prolongation
  Alors elle est refusée avec le motif « emprunt en retard » [RG-25]

# AC-027-5 — erreur
Scénario: Adhérent non éligible
  Étant donné un adhérent "EXPIRED", ou dont les pénalités "UNPAID" dépassent le seuil [RG-35]
  Quand il demande la prolongation
  Alors elle est refusée avec le motif correspondant [RG-25]

# AC-027-6 — droits
Scénario: Emprunt d'un autre adhérent
  Quand un adhérent tente de prolonger un emprunt qui ne lui appartient pas
  Alors l'action est refusée

# AC-027-7 — droits
Scénario: Prolongation au guichet
  Étant donné un emprunt prolongeable selon [RG-25]
  Quand un bibliothécaire le prolonge au nom de l'adhérent
  Alors la prolongation est appliquée selon les mêmes règles et journalisée avec l'agent
```

### US-028 — Suivre les emprunts en retard · Priorité : MUST
**En tant que** bibliothécaire, **je veux** consulter la liste des emprunts en retard, **afin de** relancer les adhérents concernés.
**Critères d'acceptation**
```gherkin
# AC-028-1 — nominal
Scénario: Liste des retards
  Étant donné 3 emprunts "OVERDUE" de 2, 10 et 30 jours de retard
  Quand le bibliothécaire consulte la liste des retards
  Alors les 3 emprunts sont listés du plus grand au plus petit retard
  Et chaque ligne affiche : titre, numéro d'exemplaire, numéro, nom et coordonnées de l'adhérent, date de retour prévue, jours de retard [RG-27], montant de pénalité provisoire [RG-36]

# AC-028-2 — filtre
Scénario: Filtrer par retard minimal
  Quand le bibliothécaire filtre sur « au moins 7 jours de retard »
  Alors seuls les emprunts de 10 et 30 jours sont affichés

# AC-028-3 — automatique
Scénario: Passage automatique en retard
  Étant donné un emprunt "ACTIVE" de date de retour prévue 07/11/2026
  Quand la date du jour devient le 08/11/2026
  Alors l'emprunt est "OVERDUE" et apparaît dans la liste avec 1 jour de retard [RG-27]

# AC-028-4 — vide
Scénario: Aucun retard
  Quand aucun emprunt n'est "OVERDUE"
  Alors un message indique qu'il n'y a aucun retard

# AC-028-5 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de consulter la liste des retards
  Alors l'action est refusée
```

### US-029 — Déclarer un exemplaire perdu · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** déclarer perdu un exemplaire qu'un adhérent ne peut pas rendre, **afin de** clore l'emprunt et facturer son remplacement.
**Critères d'acceptation**
```gherkin
# AC-029-1 — nominal
Scénario: Perte déclarée d'un emprunt en retard
  Étant donné un emprunt "OVERDUE" de 4 jours
  Quand le bibliothécaire déclare la perte et confirme
  Alors l'emprunt passe "LOST" et l'exemplaire passe "LOST" [RG-28]
  Et une pénalité de retard pour 4 jours [RG-36] et une pénalité de perte [RG-37] sont créées au statut "UNPAID"
  Et l'emprunt ne compte plus dans le quota de l'adhérent [RG-22]

# AC-029-2 — erreur
Scénario: Exemplaire non emprunté
  Étant donné un exemplaire "AVAILABLE"
  Quand le bibliothécaire tente d'en déclarer la perte via un emprunt
  Alors l'opération est refusée ; un exemplaire non emprunté se retire du fonds (US-013)

# AC-029-3 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de déclarer une perte
  Alors l'action est refusée
```

### US-030 — Remettre en circulation un exemplaire retrouvé · Priorité : COULD
**En tant que** bibliothécaire, **je veux** signaler qu'un exemplaire déclaré perdu a été retrouvé, **afin de** le proposer de nouveau au prêt.
**Critères d'acceptation**
```gherkin
# AC-030-1 — nominal
Scénario: Exemplaire retrouvé sans réservation
  Étant donné un exemplaire "LOST" et aucune réservation "PENDING" sur la notice
  Quand le bibliothécaire le déclare retrouvé
  Alors l'exemplaire passe "AVAILABLE"
  Et les pénalités liées restent inchangées ; leur remise relève de US-039 [RG-28]

# AC-030-2 — nominal
Scénario: Exemplaire retrouvé avec réservation
  Étant donné un exemplaire "LOST" et 1 réservation "PENDING" sur la notice
  Quand le bibliothécaire le déclare retrouvé
  Alors l'exemplaire passe "RESERVED" et la réservation passe "READY" [RG-26]

# AC-030-3 — erreur
Scénario: Exemplaire non perdu
  Étant donné un exemplaire qui n'est pas "LOST"
  Quand le bibliothécaire tente de le déclarer retrouvé
  Alors l'opération est refusée
```

### US-031 — Consulter l'historique de circulation d'un exemplaire · Priorité : COULD
**En tant que** bibliothécaire, **je veux** voir l'historique des emprunts d'un exemplaire, **afin d'**identifier le dernier emprunteur en cas de dégradation constatée.
**Critères d'acceptation**
```gherkin
# AC-031-1 — nominal
Scénario: Historique
  Étant donné un exemplaire emprunté 3 fois
  Quand le bibliothécaire consulte son historique
  Alors les 3 emprunts sont listés du plus récent au plus ancien avec dates d'emprunt, de retour prévue et effective, et statut

# AC-031-2 — RGPD
Scénario: Emprunteur anonymisé
  Étant donné un emprunt rendu depuis plus de [RG-19] D2 sans pénalité impayée
  Quand le bibliothécaire consulte l'historique
  Alors l'emprunt est affiché sans aucune information sur l'emprunteur

# AC-031-3 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de consulter l'historique d'un exemplaire
  Alors l'action est refusée
```

## 7. Exigences non fonctionnelles
| ID | Catégorie | Exigence vérifiable |
|---|---|---|
| NFR-02 | Performance | Emprunt et retour confirmés en ≤ 1 s pour 95 % des opérations. |
| NFR-03 | Accessibilité | Emprunt et retour réalisables au clavier seul et avec un lecteur de code-barres (saisie clavier suivie de « Entrée »), sans souris. |
| NFR-08 | Audit | Emprunts, retours, prolongations, pertes et remises en circulation journalisés avec l'agent ou l'adhérent auteur. |
| NFR-10 | Fiabilité | Jamais deux emprunts en cours sur le même exemplaire (AC-025-10). |
| NFR-11 | Exactitude | Échéances et jours de retard en jours calendaires, heure de Paris. |

## 8. Matrice de traçabilité
| US | AC | RG | Priorité |
|---|---|---|---|
| US-025 | AC-025-1 → AC-025-11 | RG-16, RG-21, RG-22, RG-23, RG-24, RG-35 | MUST |
| US-026 | AC-026-1 → AC-026-7 | RG-26, RG-27, RG-32, RG-33, RG-36 | MUST |
| US-027 | AC-027-1 → AC-027-7 | RG-25, RG-35 | MUST |
| US-028 | AC-028-1 → AC-028-5 | RG-27, RG-36 | MUST |
| US-029 | AC-029-1 → AC-029-3 | RG-22, RG-28, RG-36, RG-37 | SHOULD |
| US-030 | AC-030-1 → AC-030-3 | RG-26, RG-28 | COULD |
| US-031 | AC-031-1 → AC-031-3 | RG-19 | COULD |

## 9. Questions ouvertes
| ID | Question | Hypothèse retenue | Impact si faux |
|---|---|---|---|
| Q-09 | Durée de prêt, quota et prolongations ? Différenciés par catégorie ou par âge ? | 21 jours, 5 emprunts, 1 prolongation de 21 jours, identiques pour tous [RG-21] [RG-22] [RG-25] ; valeurs paramétrables (US-043) | Si différenciées : règles par catégorie de document ou d'adhérent à ajouter |
| Q-10 | Que devient une perte si l'exemplaire est retrouvé ? | Les pénalités restent dues ; l'administrateur peut accorder une remise (US-039) [RG-28] | Annulation automatique de la pénalité de perte |
| Q-11 | Les jours de fermeture décalent-ils les échéances et les pénalités ? | Non, jours calendaires [RG-21] [RG-27] | Ajout d'un calendrier d'ouverture et d'un report d'échéance |
| Q-12 | Faut-il des relances automatiques (avant échéance, retard) ? | Non pour la première version ; relance manuelle depuis la liste des retards (US-028) | Ajout d'une US de notification et d'un service d'envoi |

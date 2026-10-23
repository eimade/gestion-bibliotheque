---
feature: reservations
agent: spec-kit-ba
version: 1
statut: READY
sources: [besoin utilisateur (conversation du 2026-10-17), .claude/agents/1-spec-kit-ba.md, docs/specs/INDEX.md, docs/specs/SPEC-catalogue.md, docs/specs/SPEC-adherents.md, docs/specs/SPEC-emprunts.md, docs/specs/SPEC-penalites.md]
---
# SPEC — Réservations

## 1. Objectif métier
Permettre à un adhérent de se placer en file d'attente sur un livre dont tous les exemplaires sont empruntés, et lui garantir un exemplaire mis de côté dès qu'il revient, de façon équitable (premier arrivé, premier servi) et sans immobiliser les exemplaires trop longtemps.
**Indicateur de succès** : ≥ 80 % des réservations mises à disposition sont effectivement empruntées avant expiration ; délai moyen entre retour et mise de côté = 0 (mise de côté immédiate au retour).

## 2. Périmètre
- **Inclus** : réservation d'une notice par l'adhérent (ou par le personnel pour lui), annulation, attribution automatique d'un exemplaire au premier de la file, délai de retrait, expiration, liste des réservations à traiter au guichet, consultation de la file d'une notice, notification par e-mail (COULD).
- **Exclus** : réservation d'un exemplaire précis ; réservation d'un livre disponible en rayon (« mise de côté à la demande ») ; réservation de documents non encore acquis (suggestions d'achat) ; priorités particulières dans la file.

## 3. Acteurs et rôles
| Acteur | Rôle applicatif | Description |
|---|---|---|
| Adhérent | `MEMBER` | Réserve et annule ses propres réservations. |
| Bibliothécaire | `LIBRARIAN` | Réserve au nom d'un adhérent, annule, traite les réservations prêtes et expirées. |
| Administrateur | `ADMIN` | Mêmes droits que le bibliothécaire. |
| Système | — | Attribue les exemplaires et fait expirer les réservations non retirées [RG-32] [RG-33]. |

## 4. Entités métier
### ENT-Reservation (`Reservation`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Notice | ENT-Livre réservé | Oui | [RG-29] |
| Adhérent | ENT-Adherent bénéficiaire | Oui | [RG-29] [RG-30] [RG-31] |
| Date-heure de réservation | Détermine l'ordre dans la file | Oui | Automatique [RG-32] |
| Position dans la file | Rang parmi les réservations `PENDING` de la notice | Calculée | 1 = prochain servi |
| Exemplaire mis de côté | ENT-Exemplaire attribué | Si `READY` ou `FULFILLED` | [RG-32] |
| Date de mise à disposition | Passage à `READY` | Si `READY` | — |
| Date limite de retrait | Dernier jour pour emprunter | Si `READY` | [RG-33] |
| Date de clôture | Passage à un état final | Si état final | — |

**Cycle de vie** : création → `PENDING` · `PENDING` → `READY` (un exemplaire est attribué [RG-32]) · `READY` → `FULFILLED` (emprunt de l'exemplaire, US-025 [RG-24]) · `READY` → `EXPIRED` (délai de retrait dépassé [RG-33]) · `PENDING`/`READY` → `CANCELLED` (annulation [RG-34]). `FULFILLED`, `EXPIRED` et `CANCELLED` sont des états finaux.
**Réservations « actives »** = statuts `PENDING` et `READY`.

## 5. Règles de gestion
| ID | Règle | Valeur | Source/Hypothèse |
|---|---|---|---|
| RG-29 | Une réservation porte sur une notice (pas sur un exemplaire). Elle n'est possible que si la notice n'a aucun exemplaire `AVAILABLE` et au moins 1 exemplaire `ON_LOAN` ou `RESERVED`, et si l'adhérent remplit les conditions de [RG-23] hors quota d'emprunts | — | Règle métier |
| RG-30 | Nombre maximal de réservations actives par adhérent | 3 | Hypothèse Q-13 |
| RG-31 | Un adhérent ne peut pas avoir 2 réservations actives sur la même notice, ni réserver une notice dont il a déjà un exemplaire en cours d'emprunt | — | Règle métier |
| RG-32 | File d'attente « premier arrivé, premier servi » par date-heure de réservation. Dès qu'un exemplaire de la notice devient libre (retour, ajout d'exemplaire, exemplaire retrouvé, réservation `READY` expirée ou annulée), il est attribué à la première réservation `PENDING` : elle passe `READY`, l'exemplaire passe `RESERVED`. S'il n'y a aucune réservation `PENDING`, l'exemplaire passe `AVAILABLE` | — | Règle métier |
| RG-33 | Délai de retrait en jours calendaires : date limite de retrait = date de mise à disposition + délai. Le lendemain de la date limite, une réservation `READY` non retirée passe `EXPIRED` et l'exemplaire est réattribué selon [RG-32] | 3 jours | Valeur par défaut du domaine, Q-13 |
| RG-34 | Une réservation active peut être annulée à tout moment par l'adhérent concerné ou par le personnel ; l'annulation d'une réservation `READY` libère l'exemplaire selon [RG-32] | — | Règle métier |

## 6. User stories

### US-032 — Réserver un livre · Priorité : MUST
**En tant qu'**adhérent, **je veux** réserver un livre dont tous les exemplaires sont empruntés, **afin d'**être servi dès qu'un exemplaire revient.
**Critères d'acceptation**
```gherkin
# AC-032-1 — nominal
Scénario: Réservation acceptée
  Étant donné une notice dont les 2 exemplaires sont "ON_LOAN" et qui a déjà 1 réservation "PENDING"
  Et un adhérent "ACTIVE" éligible ayant 0 réservation active
  Quand il réserve cette notice depuis sa fiche
  Alors une réservation "PENDING" est créée en position 2 [RG-32]
  Et elle apparaît dans son espace avec sa position

# AC-032-2 — erreur
Scénario: Exemplaire disponible en rayon
  Étant donné une notice ayant 1 exemplaire "AVAILABLE"
  Quand l'adhérent tente de la réserver
  Alors la réservation est refusée avec le motif « un exemplaire est disponible en rayon » [RG-29]

# AC-032-3 — erreur
Scénario: Aucun exemplaire en circulation
  Étant donné une notice dont tous les exemplaires sont "LOST" ou "WITHDRAWN"
  Quand l'adhérent tente de la réserver
  Alors la réservation est refusée avec le motif « document indisponible » [RG-29]

# AC-032-4 — limite
Scénario: Nombre maximal de réservations atteint
  Étant donné un adhérent ayant exactement le nombre maximal de réservations actives [RG-30]
  Quand il tente une nouvelle réservation
  Alors elle est refusée avec le motif « nombre maximal de réservations atteint »

# AC-032-5 — erreur
Scénario: Doublon ou livre déjà emprunté
  Étant donné un adhérent ayant déjà une réservation active sur la notice, ou un exemplaire de cette notice en cours d'emprunt
  Quand il tente de la réserver
  Alors la réservation est refusée [RG-31]

# AC-032-6 — erreur
Scénario: Adhérent non éligible
  Étant donné un adhérent "EXPIRED", "SUSPENDED", avec un emprunt "OVERDUE" ou des pénalités "UNPAID" au-delà du seuil [RG-35]
  Quand il tente de réserver
  Alors la réservation est refusée et les motifs sont indiqués [RG-29] [RG-23]

# AC-032-7 — droits
Scénario: Visiteur non connecté
  Quand un visiteur non connecté tente de réserver
  Alors il est invité à se connecter et aucune réservation n'est créée

# AC-032-8 — droits
Scénario: Réservation au guichet
  Étant donné un adhérent éligible présent au guichet
  Quand un bibliothécaire réserve la notice en son nom
  Alors la réservation est créée selon les mêmes règles et journalisée avec l'agent
```

### US-033 — Annuler ma réservation · Priorité : MUST
**En tant qu'**adhérent, **je veux** annuler une réservation dont je n'ai plus besoin, **afin de** libérer ma place pour les autres.
**Critères d'acceptation**
```gherkin
# AC-033-1 — nominal
Scénario: Annulation d'une réservation en attente
  Étant donné une notice avec 3 réservations "PENDING" dont celle de l'adhérent en position 1
  Quand il l'annule
  Alors sa réservation passe "CANCELLED" [RG-34]
  Et les 2 autres réservations passent en positions 1 et 2

# AC-033-2 — nominal
Scénario: Annulation d'une réservation prête
  Étant donné une réservation "READY" de l'adhérent et 1 autre réservation "PENDING" sur la notice
  Quand il l'annule
  Alors sa réservation passe "CANCELLED"
  Et l'exemplaire mis de côté est attribué à l'autre réservation, qui passe "READY" [RG-32]

# AC-033-3 — erreur
Scénario: Réservation déjà close
  Étant donné une réservation "FULFILLED", "EXPIRED" ou "CANCELLED"
  Quand l'adhérent tente de l'annuler
  Alors l'annulation est refusée avec le motif « réservation déjà close »

# AC-033-4 — droits
Scénario: Réservation d'un autre adhérent
  Quand un adhérent tente d'annuler la réservation d'un autre adhérent
  Alors l'action est refusée
```

### US-034 — Consulter la file d'attente d'un livre · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** voir la file d'attente d'une notice, **afin de** renseigner un adhérent sur son délai probable et juger de l'opportunité d'acheter un exemplaire supplémentaire.
**Critères d'acceptation**
```gherkin
# AC-034-1 — nominal
Scénario: File d'attente
  Étant donné une notice avec 1 réservation "READY" et 3 réservations "PENDING"
  Quand le bibliothécaire consulte sa file d'attente
  Alors la réservation "READY" est affichée avec sa date limite de retrait
  Et les 3 réservations "PENDING" sont listées par position avec numéro et nom de l'adhérent et date de réservation

# AC-034-2 — vide
Scénario: Aucune réservation
  Quand le bibliothécaire consulte la file d'une notice sans réservation active
  Alors un message indique que la file est vide

# AC-034-3 — droits
Scénario: Adhérent
  Quand un adhérent consulte une notice qu'il a réservée
  Alors il voit uniquement sa propre position et le nombre total de réservations, sans l'identité des autres adhérents
```

### US-035 — Traiter les réservations au guichet · Priorité : MUST
**En tant que** bibliothécaire, **je veux** une liste des réservations prêtes et des réservations expirées, **afin de** mettre les bons exemplaires de côté et de remettre en rayon ceux qui ne sont pas venus.
**Critères d'acceptation**
```gherkin
# AC-035-1 — nominal
Scénario: Réservations prêtes
  Étant donné 2 réservations "READY"
  Quand le bibliothécaire consulte les réservations à traiter
  Alors il voit pour chacune : titre, numéro d'exemplaire, cote, numéro et nom de l'adhérent, date limite de retrait
  Et elles sont triées par date limite de retrait croissante

# AC-035-2 — automatique
Scénario: Expiration d'une réservation non retirée
  Étant donné une réservation "READY" de date limite de retrait 20/10/2026 et aucune autre réservation sur la notice
  Quand la date du jour devient le 21/10/2026
  Alors la réservation passe "EXPIRED" [RG-33]
  Et l'exemplaire passe "AVAILABLE" [RG-32]
  Et il apparaît dans la liste « à remettre en rayon »

# AC-035-3 — automatique
Scénario: Expiration avec file d'attente
  Étant donné une réservation "READY" expirée et 1 réservation "PENDING" sur la notice
  Quand l'expiration est appliquée
  Alors l'exemplaire reste "RESERVED" et est attribué à la réservation suivante, qui passe "READY" avec une nouvelle date limite [RG-32] [RG-33]
  Et la liste indique de changer le nom inscrit sur l'exemplaire mis de côté

# AC-035-4 — limite
Scénario: Retrait le dernier jour
  Étant donné une réservation "READY" de date limite de retrait 20/10/2026
  Quand l'adhérent emprunte l'exemplaire le 20/10/2026
  Alors l'emprunt est accepté et la réservation passe "FULFILLED" [RG-24]

# AC-035-5 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de consulter les réservations à traiter
  Alors l'action est refusée
```

### US-036 — Être averti qu'une réservation est prête · Priorité : COULD
**En tant qu'**adhérent, **je veux** recevoir un e-mail quand mon livre réservé est disponible, **afin de** venir le chercher avant la date limite.
**Critères d'acceptation**
```gherkin
# AC-036-1 — nominal
Scénario: E-mail de mise à disposition
  Étant donné une réservation qui passe "READY"
  Alors un e-mail est envoyé à l'adresse de l'adhérent avec le titre, la date limite de retrait [RG-33] et les horaires d'ouverture
  Et l'envoi est journalisé

# AC-036-2 — erreur
Scénario: Échec d'envoi
  Quand l'envoi de l'e-mail échoue
  Alors la réservation reste "READY" sans changement de date limite
  Et l'échec est signalé dans la liste des réservations à traiter pour un appel téléphonique
```

## 7. Exigences non fonctionnelles
| ID | Catégorie | Exigence vérifiable |
|---|---|---|
| NFR-10 | Fiabilité | Un exemplaire n'est jamais attribué à 2 réservations ; 2 réservations simultanées pour la dernière place autorisée [RG-30] n'en créent qu'une. |
| NFR-11 | Exactitude | Les expirations [RG-33] sont appliquées au plus tard à 00 h 15 (heure de Paris) le lendemain de la date limite. |
| NFR-08 | Audit | Créations, annulations, mises à disposition et expirations journalisées. |
| NFR-07 | RGPD | Un adhérent ne voit jamais l'identité des autres adhérents de la file (AC-034-3). |

## 8. Matrice de traçabilité
| US | AC | RG | Priorité |
|---|---|---|---|
| US-032 | AC-032-1 → AC-032-8 | RG-23, RG-29, RG-30, RG-31, RG-32, RG-35 | MUST |
| US-033 | AC-033-1 → AC-033-4 | RG-32, RG-34 | MUST |
| US-034 | AC-034-1 → AC-034-3 | RG-32 | SHOULD |
| US-035 | AC-035-1 → AC-035-5 | RG-24, RG-32, RG-33 | MUST |
| US-036 | AC-036-1 → AC-036-2 | RG-33 | COULD |

## 9. Questions ouvertes
| ID | Question | Hypothèse retenue | Impact si faux |
|---|---|---|---|
| Q-13 | Nombre maximal de réservations et délai de retrait ? | 3 réservations actives [RG-30] ; 3 jours calendaires de retrait [RG-33] ; paramétrables (US-043) | Ajustement de valeurs |
| Q-14 | La bibliothèque dispose-t-elle d'un service d'envoi d'e-mails ? | Oui pour US-036 (COULD) ; sans lui, l'adhérent voit le statut « prête » dans son espace (US-023) | US-036 abandonnée |

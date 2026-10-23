---
feature: penalites
agent: spec-kit-ba
version: 1
statut: READY
sources: [besoin utilisateur (conversation du 2026-10-17), .claude/agents/1-spec-kit-ba.md, docs/specs/INDEX.md, docs/specs/SPEC-adherents.md, docs/specs/SPEC-emprunts.md]
---
# SPEC — Pénalités

## 1. Objectif métier
Inciter au respect des échéances et compenser les documents perdus par des pénalités calculées automatiquement, de façon identique pour tous, et encaissées au guichet avec une traçabilité complète des sommes perçues et des remises accordées.
**Indicateur de succès** : 0 calcul manuel de pénalité ; écart nul entre les encaissements enregistrés et la caisse lors du contrôle mensuel.

## 2. Périmètre
- **Inclus** : création automatique des pénalités de retard et de perte, montant provisoire des retards en cours, encaissement au guichet, remise gracieuse, blocage des adhérents débiteurs, liste des pénalités impayées.
- **Exclus** : paiement en ligne ; paiement partiel [Q-17] ; pénalité pour document abîmé ; facturation et relances de recouvrement ; caisse et clôture comptable.

## 3. Acteurs et rôles
| Acteur | Rôle applicatif | Description |
|---|---|---|
| Bibliothécaire | `LIBRARIAN` | Consulte et encaisse les pénalités. |
| Administrateur | `ADMIN` | Encaisse et accorde les remises gracieuses. |
| Adhérent | `MEMBER` | Consulte ses propres pénalités dans son espace (US-023). |
| Système | — | Crée les pénalités lors d'un retour en retard ou d'une perte (SPEC-emprunts). |

## 4. Entités métier
### ENT-Penalite (`Fine`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Adhérent | ENT-Adherent redevable | Oui | — |
| Emprunt | ENT-Emprunt à l'origine de la pénalité | Oui | — |
| Motif | `LATE_RETURN` (retard) ou `LOST_ITEM` (perte) | Oui | — |
| Nombre de jours de retard | — | Si `LATE_RETURN` | ≥ 1 |
| Montant | En euros, 2 décimales | Oui | > 0 ; [RG-36] [RG-37] ; non modifiable |
| Date de création | — | Oui | Automatique |
| Statut | Voir cycle de vie | Oui | — |
| Date de règlement | Date d'encaissement ou de remise | Si `PAID`/`WAIVED` | — |
| Mode de paiement | `CASH`, `CARD`, `CHECK` | Si `PAID` | [RG-38] |
| Agent | Compte ayant encaissé ou accordé la remise | Si `PAID`/`WAIVED` | — |
| Motif de remise | Justification | Si `WAIVED` | [RG-39] |

**Cycle de vie** : création → `UNPAID` · `UNPAID` → `PAID` (encaissement, US-038) · `UNPAID` → `WAIVED` (remise gracieuse, US-039). `PAID` et `WAIVED` sont des états finaux.

## 5. Règles de gestion
| ID | Règle | Valeur | Source/Hypothèse |
|---|---|---|---|
| RG-35 | Seuil de blocage : un adhérent dont le total des pénalités `UNPAID` est strictement supérieur au seuil ne peut ni emprunter, ni prolonger, ni réserver [RG-23] [RG-25] [RG-29] | 5,00 € | Valeur par défaut du domaine, Q-15 |
| RG-36 | Pénalité de retard = nombre de jours de retard [RG-27] × tarif journalier, plafonnée par emprunt. Créée au retour [RG-26] ou à la déclaration de perte [RG-28] ; pour un emprunt `OVERDUE`, le montant provisoire est calculé de la même façon à la date du jour, sans créer de pénalité | Tarif : 0,20 €/jour ; plafond : 10,00 € | Valeur par défaut du domaine, Q-15 |
| RG-37 | Pénalité forfaitaire de perte, créée à la déclaration de perte [RG-28] | 20,00 € | Hypothèse Q-16 |
| RG-38 | Une pénalité se règle en une seule fois pour son montant total, au guichet, en `CASH`, `CARD` ou `CHECK`. Plusieurs pénalités d'un même adhérent peuvent être réglées dans la même opération | — | Hypothèse Q-17 |
| RG-39 | Seul un `ADMIN` peut accorder une remise gracieuse (passage `WAIVED`), avec un motif obligatoire. Une pénalité `PAID` ou `WAIVED` n'est plus modifiable | Motif : 3 à 255 caractères | Hypothèse Q-15 |

## 6. User stories

### US-037 — Calculer automatiquement les pénalités · Priorité : MUST
**En tant que** bibliothécaire, **je veux** que les pénalités soient calculées et créées automatiquement lors d'un retour en retard ou d'une perte, **afin d'**appliquer le règlement sans calcul manuel ni erreur.
**Critères d'acceptation**
```gherkin
# AC-037-1 — nominal
Scénario: Retour avec 5 jours de retard
  Étant donné un emprunt de date de retour prévue 07/11/2026
  Quand le retour est enregistré le 12/11/2026
  Alors une pénalité "LATE_RETURN" "UNPAID" de 5 jours est créée
  Et son montant est 5 × le tarif journalier [RG-36], soit 1,00 € avec les valeurs par défaut

# AC-037-2 — limite
Scénario: Plafond atteint exactement
  Étant donné un retour avec un nombre de jours de retard tel que jours × tarif = plafond [RG-36] (50 jours avec les valeurs par défaut)
  Alors le montant est égal au plafond (10,00 €)

# AC-037-3 — limite
Scénario: Plafond dépassé
  Étant donné un retour avec 1 jour de plus que dans AC-037-2 (51 jours)
  Alors le montant reste égal au plafond (10,00 €) [RG-36]

# AC-037-4 — limite
Scénario: Aucun retard
  Étant donné un retour le jour de la date de retour prévue
  Alors aucune pénalité n'est créée [RG-27]

# AC-037-5 — nominal
Scénario: Perte déclarée avec retard
  Étant donné un emprunt "OVERDUE" de 4 jours déclaré perdu
  Alors 2 pénalités "UNPAID" sont créées : "LATE_RETURN" de 0,80 € [RG-36] et "LOST_ITEM" de 20,00 € [RG-37]

# AC-037-6 — nominal
Scénario: Montant provisoire d'un retard en cours
  Étant donné un emprunt "OVERDUE" de 3 jours
  Quand le bibliothécaire ou l'adhérent consulte cet emprunt
  Alors le montant provisoire 0,60 € est affiché comme « provisoire » [RG-36]
  Et aucune pénalité n'existe encore pour cet emprunt

# AC-037-7 — blocage
Scénario: Dépassement du seuil
  Étant donné un adhérent ayant 4,50 € de pénalités "UNPAID"
  Quand un retour crée une pénalité de 1,00 €
  Alors son total impayé est 5,50 €, supérieur au seuil [RG-35]
  Et toute tentative d'emprunt, de prolongation ou de réservation est refusée avec le motif « pénalités impayées »
```

### US-038 — Encaisser une pénalité · Priorité : MUST
**En tant que** bibliothécaire, **je veux** enregistrer le paiement des pénalités d'un adhérent, **afin de** solder sa dette et le débloquer.
**Critères d'acceptation**
```gherkin
# AC-038-1 — nominal
Scénario: Paiement de plusieurs pénalités
  Étant donné un adhérent ayant 2 pénalités "UNPAID" de 1,00 € et 20,00 €
  Quand le bibliothécaire sélectionne les 2 pénalités, choisit le mode "CARD" et valide l'encaissement de 21,00 €
  Alors les 2 pénalités passent "PAID" avec la date du jour, le mode "CARD" et l'agent [RG-38]
  Et le total impayé de l'adhérent est 0,00 €
  Et l'adhérent peut de nouveau emprunter s'il remplit les autres conditions [RG-23]

# AC-038-2 — erreur
Scénario: Paiement partiel
  Quand le bibliothécaire tente d'enregistrer un montant inférieur au total des pénalités sélectionnées
  Alors l'encaissement est refusé avec le motif « le paiement partiel n'est pas autorisé » [RG-38]

# AC-038-3 — erreur
Scénario: Pénalité déjà réglée
  Étant donné une pénalité "PAID" ou "WAIVED"
  Quand le bibliothécaire tente de l'encaisser
  Alors l'opération est refusée [RG-39]

# AC-038-4 — erreur
Scénario: Mode de paiement manquant
  Quand le bibliothécaire valide sans mode de paiement
  Alors l'encaissement est refusé

# AC-038-5 — concurrence
Scénario: Double encaissement simultané
  Quand deux agents encaissent la même pénalité au même moment
  Alors un seul encaissement est enregistré et l'autre est refusé

# AC-038-6 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente d'enregistrer un paiement
  Alors l'action est refusée
```

### US-039 — Accorder une remise gracieuse · Priorité : SHOULD
**En tant qu'**administrateur, **je veux** annuler une pénalité pour un motif légitime, **afin de** traiter les situations exceptionnelles (erreur, exemplaire retrouvé, cas social).
**Critères d'acceptation**
```gherkin
# AC-039-1 — nominal
Scénario: Remise accordée
  Étant donné une pénalité "UNPAID" de 20,00 €
  Quand l'administrateur accorde une remise avec le motif "Exemplaire retrouvé en bon état"
  Alors la pénalité passe "WAIVED" avec la date du jour, l'agent et le motif [RG-39]
  Et elle n'est plus comptée dans le total impayé de l'adhérent

# AC-039-2 — erreur
Scénario: Motif absent
  Quand l'administrateur accorde une remise sans motif
  Alors l'opération est refusée [RG-39]

# AC-039-3 — erreur
Scénario: Pénalité déjà payée
  Étant donné une pénalité "PAID"
  Quand l'administrateur tente une remise
  Alors l'opération est refusée [RG-39]

# AC-039-4 — droits
Scénario: Bibliothécaire non autorisé
  Quand un utilisateur "LIBRARIAN" tente d'accorder une remise
  Alors l'action est refusée [RG-39]
```

### US-040 — Consulter les pénalités impayées · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** la liste des adhérents ayant des pénalités impayées, **afin de** les relancer en priorité.
**Critères d'acceptation**
```gherkin
# AC-040-1 — nominal
Scénario: Liste des débiteurs
  Étant donné 3 adhérents ayant respectivement 0,40 €, 6,00 € et 20,80 € de pénalités "UNPAID"
  Quand le bibliothécaire consulte les pénalités impayées
  Alors les 3 adhérents sont listés par montant décroissant avec numéro, nom, coordonnées, nombre de pénalités et total
  Et les adhérents au-delà du seuil [RG-35] sont signalés « bloqué »

# AC-040-2 — vide
Scénario: Aucune pénalité impayée
  Quand aucune pénalité n'est "UNPAID"
  Alors un message indique qu'il n'y a aucune pénalité impayée

# AC-040-3 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de consulter la liste des pénalités impayées
  Alors l'action est refusée
```

## 7. Exigences non fonctionnelles
| ID | Catégorie | Exigence vérifiable |
|---|---|---|
| NFR-05 | Langue | Montants affichés en euros avec 2 décimales et virgule (« 1,00 € »). |
| NFR-08 | Audit | Créations, encaissements et remises journalisés ; un encaissement ou une remise ne peut être ni modifié ni supprimé. |
| NFR-10 | Fiabilité | Calculs monétaires exacts au centime, sans arrondi intermédiaire ; jamais de double encaissement (AC-038-5). |
| NFR-11 | Exactitude | Jours de retard en jours calendaires, heure de Paris. |

## 8. Matrice de traçabilité
| US | AC | RG | Priorité |
|---|---|---|---|
| US-037 | AC-037-1 → AC-037-7 | RG-27, RG-35, RG-36, RG-37 | MUST |
| US-038 | AC-038-1 → AC-038-6 | RG-23, RG-38, RG-39 | MUST |
| US-039 | AC-039-1 → AC-039-4 | RG-39 | SHOULD |
| US-040 | AC-040-1 → AC-040-3 | RG-35 | SHOULD |

## 9. Questions ouvertes
| ID | Question | Hypothèse retenue | Impact si faux |
|---|---|---|---|
| Q-15 | Tarifs, plafond, seuil de blocage, auteur des remises ? | 0,20 €/jour, plafond 10,00 € par emprunt, blocage au-delà de 5,00 €, remise par l'administrateur seul [RG-35] [RG-36] [RG-39] ; valeurs paramétrables (US-043) | Ajustement de valeurs ou de droits |
| Q-16 | Comment facturer une perte ? | Forfait unique de 20,00 € [RG-37] | Facturation au prix réel de l'exemplaire (ajout d'un prix d'acquisition) |
| Q-17 | Le paiement partiel est-il autorisé ? | Non [RG-38] | Ajout d'un solde restant dû par pénalité |

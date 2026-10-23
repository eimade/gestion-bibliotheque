---
feature: authentification
agent: spec-kit-ba
version: 1
statut: READY
sources: [besoin utilisateur (conversation du 2026-10-17), .claude/agents/1-spec-kit-ba.md, docs/specs/INDEX.md]
---
# SPEC — Authentification et comptes utilisateurs

## 1. Objectif métier
Garantir que seules les personnes autorisées accèdent aux fonctions de gestion, et que chaque adhérent n'accède qu'à ses propres informations. Chaque action sensible doit pouvoir être imputée à une personne identifiée.
**Indicateur de succès** : 100 % des actions de gestion sont rattachées à un compte nominatif ; 0 compte partagé entre plusieurs membres du personnel.

## 2. Périmètre
- **Inclus** : connexion, déconnexion, changement de mot de passe, mot de passe temporaire, verrouillage après échecs, création, désactivation et réactivation des comptes du personnel, réinitialisation d'un mot de passe par le personnel.
- **Exclus** : réinitialisation en libre-service par e-mail (« mot de passe oublié ») — voir Q-02 ; authentification unique avec un annuaire externe ; double authentification ; création des comptes adhérents (traitée par l'inscription, US-016).

## 3. Acteurs et rôles
| Acteur | Rôle applicatif | Description |
|---|---|---|
| Adhérent | `MEMBER` | Se connecte à son espace personnel. |
| Bibliothécaire | `LIBRARIAN` | Se connecte au poste de travail ; réinitialise le mot de passe des adhérents. |
| Administrateur | `ADMIN` | Gère les comptes du personnel. |

## 4. Entités métier
### ENT-Compte (`UserAccount`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Identifiant de connexion | Adresse e-mail | Oui | Unique parmi tous les comptes, insensible à la casse [RG-14] |
| Mot de passe | Secret de connexion | Oui | Respecte la politique [RG-01] ; jamais affiché ni communiqué en clair après sa saisie |
| Nom, prénom | Identité de l'utilisateur | Oui | — |
| Rôle | `MEMBER`, `LIBRARIAN` ou `ADMIN` | Oui | Un seul rôle par compte |
| Adhérent rattaché | Lien vers ENT-Adherent | Si rôle `MEMBER` | [RG-04] |
| Mot de passe temporaire | Indique que le mot de passe doit être changé | Oui | [RG-05] |
| Nombre d'échecs consécutifs | Compteur d'échecs de connexion | Oui | Remis à 0 après une connexion réussie |
| Fin de verrouillage | Date-heure de fin du verrouillage | Non | [RG-02] |
| Statut | Voir cycle de vie | Oui | — |

**Cycle de vie** : `ACTIVE` → `LOCKED` (échecs consécutifs [RG-02]) → `ACTIVE` (fin du délai [RG-02] ou déblocage par le personnel, US-006) · `ACTIVE`/`LOCKED` → `DISABLED` (désactivation, US-005, ou archivage de l'adhérent, US-022) → `ACTIVE` (réactivation, US-005 ; impossible pour un adhérent archivé).

## 5. Règles de gestion
| ID | Règle | Valeur | Source/Hypothèse |
|---|---|---|---|
| RG-01 | Politique de mot de passe : longueur minimale, au moins 1 majuscule, 1 minuscule, 1 chiffre ; différent des 3 derniers mots de passe | 12 caractères ; historique de 3 | Hypothèse Q-01 (recommandations ANSSI) |
| RG-02 | Verrouillage automatique du compte après N échecs consécutifs, pendant une durée D | N = 5 ; D = 15 minutes | Hypothèse Q-01 |
| RG-03 | Déconnexion automatique après une période d'inactivité | 30 minutes | Hypothèse Q-01 |
| RG-04 | Un compte `MEMBER` est rattaché à exactement un adhérent, et un adhérent a au plus un compte. Un compte `LIBRARIAN`/`ADMIN` n'est rattaché à aucun adhérent | — | Règle métier |
| RG-05 | Un mot de passe défini par un tiers (création ou réinitialisation) est temporaire : il doit être changé à la première connexion avant tout autre accès | — | Règle métier |
| RG-06 | Seul un `ADMIN` crée, désactive ou réactive un compte du personnel. Un `ADMIN` ne peut pas désactiver son propre compte ; il doit toujours exister au moins 1 compte `ADMIN` `ACTIVE` | Minimum 1 ADMIN actif | Règle métier |

## 6. User stories

### US-001 — Se connecter · Priorité : MUST
**En tant qu'**utilisateur (adhérent, bibliothécaire ou administrateur), **je veux** me connecter avec mon e-mail et mon mot de passe, **afin d'**accéder aux fonctions correspondant à mon rôle.
**Critères d'acceptation**
```gherkin
# AC-001-1 — nominal
Scénario: Connexion réussie d'un bibliothécaire
  Étant donné un compte "LIBRARIAN" au statut "ACTIVE" avec un mot de passe non temporaire
  Quand l'utilisateur saisit son e-mail et son mot de passe corrects
  Alors il est connecté
  Et il accède à l'espace de gestion
  Et son compteur d'échecs est remis à 0

# AC-001-2 — nominal adhérent
Scénario: Connexion réussie d'un adhérent
  Étant donné un compte "MEMBER" au statut "ACTIVE" rattaché à un adhérent
  Quand l'adhérent saisit ses identifiants corrects
  Alors il accède à son espace personnel et à aucune fonction de gestion

# AC-001-3 — erreur
Scénario: Identifiants incorrects
  Étant donné un compte au statut "ACTIVE" avec 0 échec
  Quand l'utilisateur saisit un mot de passe incorrect
  Alors la connexion est refusée avec le motif « identifiants incorrects »
  Et le message ne révèle pas si l'e-mail existe
  Et le compteur d'échecs du compte passe à 1

# AC-001-4 — limite
Scénario: Verrouillage après trop d'échecs
  Étant donné un compte au statut "ACTIVE" ayant atteint le nombre d'échecs consécutifs [RG-02] moins 1
  Quand l'utilisateur saisit à nouveau un mot de passe incorrect
  Alors le compte passe au statut "LOCKED" pour la durée [RG-02]
  Et toute tentative pendant cette durée est refusée, même avec le bon mot de passe

# AC-001-5 — erreur
Scénario: Compte désactivé
  Étant donné un compte au statut "DISABLED"
  Quand l'utilisateur saisit des identifiants corrects
  Alors la connexion est refusée avec le motif « compte désactivé, contactez la bibliothèque »

# AC-001-6 — limite
Scénario: Fin du verrouillage
  Étant donné un compte "LOCKED" dont la durée de verrouillage [RG-02] est écoulée
  Quand l'utilisateur saisit ses identifiants corrects
  Alors il est connecté et le compte repasse au statut "ACTIVE"

# AC-001-7 — mot de passe temporaire
Scénario: Première connexion avec un mot de passe temporaire
  Étant donné un compte "ACTIVE" dont le mot de passe est temporaire [RG-05]
  Quand l'utilisateur se connecte avec ce mot de passe
  Alors il est obligé de choisir un nouveau mot de passe (US-003) avant d'accéder à toute autre fonction

# AC-001-8 — inactivité
Scénario: Expiration de session pour inactivité
  Étant donné un utilisateur connecté sans aucune action depuis la durée [RG-03]
  Quand il tente une action
  Alors l'action n'est pas exécutée et il doit se reconnecter
```

### US-002 — Se déconnecter · Priorité : MUST
**En tant qu'**utilisateur connecté, **je veux** me déconnecter, **afin qu'**aucune autre personne utilisant le même poste n'agisse en mon nom.
**Critères d'acceptation**
```gherkin
# AC-002-1 — nominal
Scénario: Déconnexion
  Étant donné un utilisateur connecté
  Quand il se déconnecte
  Alors il revient sur le catalogue public
  Et l'action « page précédente » du navigateur ne donne accès à aucune donnée protégée

# AC-002-2 — erreur
Scénario: Accès à une page protégée après déconnexion
  Étant donné un utilisateur qui vient de se déconnecter
  Quand il tente d'ouvrir une page de gestion
  Alors l'accès est refusé et l'écran de connexion est proposé
```

### US-003 — Changer mon mot de passe · Priorité : MUST
**En tant qu'**utilisateur connecté, **je veux** changer mon mot de passe, **afin de** protéger mon compte.
**Critères d'acceptation**
```gherkin
# AC-003-1 — nominal
Scénario: Changement réussi
  Étant donné un utilisateur connecté
  Quand il saisit son mot de passe actuel et un nouveau mot de passe conforme à [RG-01], confirmé à l'identique
  Alors le nouveau mot de passe remplace l'ancien
  Et le mot de passe n'est plus temporaire
  Et l'ancien mot de passe ne permet plus de se connecter

# AC-003-2 — erreur
Scénario: Mot de passe actuel incorrect
  Quand l'utilisateur saisit un mot de passe actuel erroné
  Alors le changement est refusé avec le motif « mot de passe actuel incorrect »

# AC-003-3 — erreur
Scénario: Nouveau mot de passe non conforme
  Quand l'utilisateur saisit un nouveau mot de passe de longueur [RG-01] moins 1 caractère
  Alors le changement est refusé et la règle non respectée est indiquée

# AC-003-4 — erreur
Scénario: Réutilisation d'un ancien mot de passe
  Quand l'utilisateur saisit comme nouveau mot de passe l'un de ses derniers mots de passe [RG-01]
  Alors le changement est refusé avec le motif « mot de passe déjà utilisé récemment »

# AC-003-5 — erreur
Scénario: Confirmation différente
  Quand la confirmation diffère du nouveau mot de passe
  Alors le changement est refusé avec le motif « les mots de passe ne correspondent pas »
```

### US-004 — Créer un compte du personnel · Priorité : MUST
**En tant qu'**administrateur, **je veux** créer un compte bibliothécaire ou administrateur, **afin que** chaque agent dispose d'un accès nominatif.
**Critères d'acceptation**
```gherkin
# AC-004-1 — nominal
Scénario: Création d'un compte bibliothécaire
  Étant donné un administrateur connecté
  Quand il crée un compte avec nom, prénom, e-mail inconnu, rôle "LIBRARIAN" et un mot de passe initial conforme à [RG-01]
  Alors le compte est créé au statut "ACTIVE" avec un mot de passe temporaire [RG-05]
  Et la création est journalisée

# AC-004-2 — erreur
Scénario: E-mail déjà utilisé
  Quand l'administrateur crée un compte avec un e-mail déjà utilisé par un autre compte, quelle que soit la casse [RG-14]
  Alors la création est refusée avec le motif « e-mail déjà utilisé »

# AC-004-3 — erreur
Scénario: Rôle MEMBER interdit
  Quand l'administrateur tente de créer un compte du personnel avec le rôle "MEMBER"
  Alors la création est refusée : les comptes adhérents se créent uniquement à l'inscription (US-016) [RG-04]

# AC-004-4 — droits
Scénario: Un bibliothécaire ne peut pas créer de compte du personnel
  Étant donné un utilisateur connecté avec le rôle "LIBRARIAN"
  Quand il tente de créer un compte du personnel
  Alors l'action est refusée pour droits insuffisants [RG-06]
```

### US-005 — Désactiver ou réactiver un compte du personnel · Priorité : MUST
**En tant qu'**administrateur, **je veux** désactiver le compte d'un agent qui quitte la bibliothèque, **afin de** lui retirer immédiatement tout accès.
**Critères d'acceptation**
```gherkin
# AC-005-1 — nominal
Scénario: Désactivation
  Étant donné un compte "LIBRARIAN" au statut "ACTIVE"
  Quand l'administrateur le désactive
  Alors le compte passe au statut "DISABLED"
  Et l'agent, s'il était connecté, ne peut plus exécuter aucune action

# AC-005-2 — nominal
Scénario: Réactivation
  Étant donné un compte du personnel au statut "DISABLED"
  Quand l'administrateur le réactive
  Alors le compte repasse au statut "ACTIVE" avec un compteur d'échecs à 0

# AC-005-3 — erreur
Scénario: Auto-désactivation interdite
  Quand un administrateur tente de désactiver son propre compte
  Alors l'action est refusée [RG-06]

# AC-005-4 — limite
Scénario: Dernier administrateur actif
  Étant donné qu'il existe exactement 1 compte "ADMIN" au statut "ACTIVE" (minimum [RG-06])
  Quand un administrateur tente de désactiver ce compte
  Alors l'action est refusée avec le motif « au moins un administrateur actif est requis »

# AC-005-5 — droits
Scénario: Action réservée à l'administrateur
  Étant donné un utilisateur "LIBRARIAN"
  Quand il tente de désactiver un compte du personnel
  Alors l'action est refusée pour droits insuffisants
```

### US-006 — Réinitialiser le mot de passe d'un utilisateur · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** attribuer un nouveau mot de passe temporaire à un adhérent qui a oublié le sien, **afin qu'**il retrouve l'accès à son espace sans attendre.
**Critères d'acceptation**
```gherkin
# AC-006-1 — nominal
Scénario: Réinitialisation du mot de passe d'un adhérent
  Étant donné un compte "MEMBER" au statut "ACTIVE" ou "LOCKED"
  Quand un bibliothécaire lui attribue un mot de passe conforme à [RG-01]
  Alors ce mot de passe est temporaire [RG-05]
  Et le compte est au statut "ACTIVE" avec un compteur d'échecs à 0
  Et l'opération est journalisée sans le mot de passe

# AC-006-2 — erreur
Scénario: Compte désactivé
  Étant donné un compte "MEMBER" au statut "DISABLED"
  Quand un bibliothécaire tente de réinitialiser son mot de passe
  Alors l'action est refusée avec le motif « compte désactivé »

# AC-006-3 — droits
Scénario: Réinitialisation d'un compte du personnel
  Étant donné un utilisateur "LIBRARIAN"
  Quand il tente de réinitialiser le mot de passe d'un compte "LIBRARIAN" ou "ADMIN"
  Alors l'action est refusée ; seul un "ADMIN" peut le faire [RG-06]
```

## 7. Exigences non fonctionnelles
| ID | Catégorie | Exigence vérifiable |
|---|---|---|
| NFR-06 | Sécurité | Voir [INDEX](INDEX.md) : toute donnée hors catalogue public exige une authentification. |
| NFR-08 | Audit | Connexions réussies, échecs, verrouillages, créations, désactivations et réinitialisations sont journalisés (jamais le mot de passe). |
| NFR-03 | Accessibilité | Le formulaire de connexion est utilisable au clavier seul et compatible avec les lecteurs d'écran. |

## 8. Matrice de traçabilité
| US | AC | RG | Priorité |
|---|---|---|---|
| US-001 | AC-001-1 → AC-001-8 | RG-02, RG-03, RG-05 | MUST |
| US-002 | AC-002-1 → AC-002-2 | — | MUST |
| US-003 | AC-003-1 → AC-003-5 | RG-01, RG-05 | MUST |
| US-004 | AC-004-1 → AC-004-4 | RG-01, RG-04, RG-05, RG-06, RG-14 | MUST |
| US-005 | AC-005-1 → AC-005-5 | RG-06 | MUST |
| US-006 | AC-006-1 → AC-006-3 | RG-01, RG-05, RG-06 | SHOULD |

## 9. Questions ouvertes
| ID | Question | Hypothèse retenue | Impact si faux |
|---|---|---|---|
| Q-01 | Quelles politiques de mot de passe, de verrouillage et d'inactivité appliquer ? | Valeurs RG-01 à RG-03 (recommandations ANSSI usuelles) | Ajustement de valeurs uniquement |
| Q-02 | Faut-il une réinitialisation « mot de passe oublié » par e-mail en libre-service ? | Non pour la première version : le personnel réinitialise au guichet (US-006). Pas d'envoi d'e-mail | Ajout d'une US et d'un service d'envoi d'e-mails |

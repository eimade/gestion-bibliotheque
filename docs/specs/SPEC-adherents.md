---
feature: adherents
agent: spec-kit-ba
version: 1
statut: READY
sources: [besoin utilisateur (conversation du 2026-10-17), .claude/agents/1-spec-kit-ba.md, docs/specs/INDEX.md, docs/specs/SPEC-authentification.md]
---
# SPEC — Adhérents et espace adhérent

## 1. Objectif métier
Tenir un fichier des adhérents exact et conforme au RGPD, et donner à chaque adhérent une vue autonome de sa situation (emprunts, échéances, réservations, pénalités) pour réduire les demandes au guichet et les retards.
**Indicateur de succès** : inscription d'un adhérent au guichet en ≤ 3 minutes ; ≥ 50 % des adhérents actifs se connectent au moins une fois à leur espace dans les 3 mois suivant la mise en service.

## 2. Périmètre
- **Inclus** : inscription (avec création du compte adhérent), recherche, fiche adhérent, modification des coordonnées, renouvellement, suspension, réactivation, archivage (anonymisation), espace personnel de l'adhérent, règles de conservation des données.
- **Exclus** : cotisation payante et encaissement de l'adhésion [Q-06] ; carte d'adhérent imprimée ; adhésions familiales ou collectives (écoles) ; communication par e-mail ou SMS ; import d'un fichier d'adhérents existant.

## 3. Acteurs et rôles
| Acteur | Rôle applicatif | Description |
|---|---|---|
| Bibliothécaire | `LIBRARIAN` | Inscrit, recherche, consulte, modifie, renouvelle, suspend et archive les adhérents. |
| Administrateur | `ADMIN` | Mêmes droits que le bibliothécaire. |
| Adhérent | `MEMBER` | Consulte son propre espace ; modifie ses coordonnées de contact (COULD). |

## 4. Entités métier
### ENT-Adherent (`Member`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Numéro d'adhérent | Identifiant présenté au guichet | Oui | [RG-13] |
| Nom, prénom | Identité | Oui | 1 à 100 caractères chacun |
| Date de naissance | — | Oui | Antérieure à la date du jour ; détermine [RG-20] |
| E-mail | Contact et identifiant de connexion | Oui | [RG-14] |
| Téléphone | Contact | Non | Numéro français ou international valide |
| Adresse postale | Rue, code postal, ville | Oui | Code postal de 5 chiffres pour la France |
| Représentant légal | Nom, prénom et téléphone | Si mineur [RG-20] | — |
| Date d'inscription | Date de la première inscription | Oui | Automatique, non modifiable |
| Date d'expiration de l'adhésion | Fin de validité | Oui | [RG-15] |
| Statut | Voir cycle de vie | Oui | — |
| Motif de suspension | Raison de la suspension | Si `SUSPENDED` | [RG-17] |

**Finalité des données** : gestion des prêts, réservations et pénalités, et contact de l'adhérent au sujet de ses documents. **Accès** : personnel (`LIBRARIAN`, `ADMIN`) et l'adhérent lui-même pour ses propres données. **Conservation** : [RG-19].

**Cycle de vie** : inscription → `ACTIVE` · `ACTIVE` → `EXPIRED` (lendemain de la date d'expiration [RG-16]) · `EXPIRED` → `ACTIVE` (renouvellement, US-020) · `ACTIVE`/`EXPIRED` → `SUSPENDED` (suspension, US-021) · `SUSPENDED` → `ACTIVE` ou `EXPIRED` selon la date d'expiration (réactivation, US-021) · `ACTIVE`/`EXPIRED`/`SUSPENDED` → `ARCHIVED` (archivage, US-022, état final).

## 5. Règles de gestion
| ID | Règle | Valeur | Source/Hypothèse |
|---|---|---|---|
| RG-13 | Numéro d'adhérent attribué automatiquement, unique, jamais modifié ni réattribué ; format distinct du numéro d'exemplaire pour éviter toute confusion au scan | « A » + 6 chiffres (ex. A000123) | Hypothèse |
| RG-14 | E-mail obligatoire, unique parmi tous les comptes et adhérents, comparé sans tenir compte de la casse | — | Hypothèse Q-07 |
| RG-15 | Durée de l'adhésion. Inscription : expiration = date du jour + durée. Renouvellement : expiration = (la plus tardive entre la date du jour et l'expiration actuelle) + durée ; autorisé au plus tôt N jours avant l'expiration | Durée : 12 mois ; N = 30 jours | Hypothèse Q-06 |
| RG-16 | Un adhérent passe `EXPIRED` le lendemain de sa date d'expiration. Un adhérent `EXPIRED`, `SUSPENDED` ou `ARCHIVED` ne peut ni emprunter, ni prolonger, ni réserver ; un adhérent `EXPIRED` ou `SUSPENDED` peut rendre ses documents, payer ses pénalités et consulter son espace | — | Règle métier |
| RG-17 | La suspension exige un motif ; elle ne modifie pas la date d'expiration | Motif : 3 à 255 caractères | Règle métier |
| RG-18 | Archivage autorisé uniquement si l'adhérent n'a aucun emprunt `ACTIVE` ou `OVERDUE`, aucune réservation `PENDING` ou `READY`, aucune pénalité `UNPAID`. Effet irréversible : nom, prénom, date de naissance, coordonnées et représentant légal effacés ; le numéro, l'année de naissance et le code postal sont conservés à des fins statistiques ; le compte adhérent passe `DISABLED` | — | RGPD (minimisation) |
| RG-19 | Conservation : un adhérent `EXPIRED` depuis plus de D1 est proposé à l'archivage. Le lien entre un adhérent et un emprunt rendu est effacé D2 après le retour, sauf si une pénalité liée est `UNPAID` (l'emprunt reste dans les statistiques sans identité) | D1 = 3 ans ; D2 = 4 mois | Hypothèse Q-08 (recommandations CNIL pour les bibliothèques) |
| RG-20 | Un adhérent mineur de moins de A ans à l'inscription doit avoir un représentant légal renseigné | A = 15 ans | Hypothèse Q-05 (consentement parental, RGPD France) |

## 6. User stories

### US-016 — Inscrire un adhérent · Priorité : MUST
**En tant que** bibliothécaire, **je veux** inscrire une nouvelle personne, **afin qu'**elle puisse emprunter et accéder à son espace.
**Critères d'acceptation**
```gherkin
# AC-016-1 — nominal
Scénario: Inscription d'un adulte
  Étant donné un bibliothécaire connecté et la date du jour 17/10/2026
  Quand il saisit nom, prénom, date de naissance 12/03/1990, un e-mail inconnu, une adresse complète et un mot de passe initial conforme à [RG-01]
  Alors l'adhérent est créé au statut "ACTIVE" avec un numéro attribué [RG-13]
  Et sa date d'expiration est le 17/10/2027 [RG-15]
  Et un compte "MEMBER" rattaché est créé avec ce mot de passe temporaire [RG-04] [RG-05]

# AC-016-2 — nominal
Scénario: Inscription d'un mineur avec représentant légal
  Quand le bibliothécaire inscrit une personne de 10 ans avec un représentant légal renseigné
  Alors l'adhérent est créé au statut "ACTIVE"

# AC-016-3 — erreur
Scénario: Mineur sans représentant légal
  Quand le bibliothécaire inscrit une personne âgée de moins de [RG-20] sans représentant légal
  Alors l'inscription est refusée avec le motif « représentant légal obligatoire »

# AC-016-4 — limite
Scénario: Âge exactement égal au seuil
  Quand le bibliothécaire inscrit une personne dont c'est le jour anniversaire de l'âge [RG-20]
  Alors le représentant légal n'est pas exigé

# AC-016-5 — erreur
Scénario: E-mail déjà utilisé
  Étant donné un adhérent existant d'e-mail "jean.martin@exemple.fr"
  Quand le bibliothécaire inscrit une personne avec "Jean.Martin@exemple.fr"
  Alors l'inscription est refusée avec le motif « e-mail déjà utilisé » [RG-14]

# AC-016-6 — erreur
Scénario: Champs obligatoires manquants ou invalides
  Quand le bibliothécaire valide sans adresse, ou avec une date de naissance postérieure à la date du jour
  Alors l'inscription est refusée et chaque champ en erreur est signalé

# AC-016-7 — doublon probable
Scénario: Homonyme
  Étant donné un adhérent existant avec les mêmes nom, prénom et date de naissance
  Quand le bibliothécaire valide l'inscription
  Alors un avertissement présente l'adhérent existant
  Et l'inscription n'est créée qu'après confirmation explicite

# AC-016-8 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente d'inscrire un adhérent
  Alors l'action est refusée
```

### US-017 — Rechercher un adhérent · Priorité : MUST
**En tant que** bibliothécaire, **je veux** retrouver un adhérent par son numéro, son nom ou son e-mail, **afin de** traiter sa demande au guichet.
**Critères d'acceptation**
```gherkin
# AC-017-1 — nominal
Scénario: Recherche par numéro scanné
  Quand le bibliothécaire saisit ou scanne le numéro "A000123"
  Alors la fiche de cet adhérent s'ouvre directement

# AC-017-2 — nominal
Scénario: Recherche par nom partiel
  Étant donné les adhérents "Martin Jean" et "Martinez Ana"
  Quand le bibliothécaire recherche "mart"
  Alors les 2 adhérents sont listés avec numéro, nom, prénom, date de naissance, statut et date d'expiration

# AC-017-3 — vide
Scénario: Aucun résultat
  Quand le bibliothécaire recherche "zzz"
  Alors un message indique qu'aucun adhérent ne correspond

# AC-017-4 — limite
Scénario: Adhérents archivés
  Étant donné un adhérent "ARCHIVED"
  Quand le bibliothécaire recherche par son ancien nom
  Alors il n'est pas trouvé ; seule une recherche par numéro le retrouve, sans données personnelles [RG-18]

# AC-017-5 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de rechercher des adhérents
  Alors l'action est refusée
```

### US-018 — Consulter la fiche d'un adhérent · Priorité : MUST
**En tant que** bibliothécaire, **je veux** voir en un écran la situation complète d'un adhérent, **afin de** savoir immédiatement s'il peut emprunter et pourquoi.
**Critères d'acceptation**
```gherkin
# AC-018-1 — nominal
Scénario: Situation complète
  Étant donné un adhérent "ACTIVE" avec 2 emprunts dont 1 "OVERDUE", 1 réservation "READY" et 1,40 € de pénalités "UNPAID"
  Quand le bibliothécaire ouvre sa fiche
  Alors il voit ses coordonnées, son statut, sa date d'expiration
  Et ses 2 emprunts en cours avec titre, numéro d'exemplaire, date de retour prévue, le retard étant signalé
  Et sa réservation avec sa date limite de retrait
  Et le total des pénalités impayées "1,40 €"
  Et l'indication « emprunt impossible : emprunt en retard » [RG-23]

# AC-018-2 — nominal
Scénario: Adhérent éligible
  Étant donné un adhérent "ACTIVE" sans retard, sans pénalité et sous la limite d'emprunts [RG-22]
  Quand le bibliothécaire ouvre sa fiche
  Alors l'indication « peut emprunter N document(s) » est affichée, N étant le nombre restant avant la limite [RG-22]

# AC-018-3 — alerte
Scénario: Adhésion bientôt expirée
  Étant donné un adhérent dont l'adhésion expire dans moins de [RG-15] N jours
  Quand le bibliothécaire ouvre sa fiche
  Alors une alerte propose le renouvellement

# AC-018-4 — erreur
Scénario: Adhérent inexistant
  Quand le bibliothécaire demande un numéro d'adhérent inexistant
  Alors un message indique que l'adhérent est introuvable

# AC-018-5 — droits
Scénario: Un adhérent ne voit pas la fiche d'un autre
  Étant donné un utilisateur "MEMBER" rattaché à l'adhérent A000001
  Quand il tente d'ouvrir la fiche de l'adhérent A000002
  Alors l'accès est refusé et aucune donnée de A000002 n'est transmise
```

### US-019 — Modifier les informations d'un adhérent · Priorité : MUST
**En tant que** bibliothécaire, **je veux** mettre à jour les informations d'un adhérent, **afin que** ses coordonnées restent exactes.
**Critères d'acceptation**
```gherkin
# AC-019-1 — nominal
Scénario: Changement d'adresse et d'e-mail
  Étant donné un adhérent "ACTIVE"
  Quand le bibliothécaire modifie son adresse et son e-mail vers un e-mail inconnu
  Alors les modifications sont enregistrées
  Et le nouvel e-mail devient l'identifiant de connexion de son compte
  Et la modification est journalisée avec les valeurs avant/après

# AC-019-2 — erreur
Scénario: E-mail déjà utilisé
  Quand le bibliothécaire saisit l'e-mail d'un autre compte [RG-14]
  Alors la modification est refusée

# AC-019-3 — erreur
Scénario: Champs non modifiables
  Quand le bibliothécaire tente de modifier le numéro d'adhérent ou la date d'inscription
  Alors la modification est refusée [RG-13]

# AC-019-4 — erreur
Scénario: Adhérent archivé
  Étant donné un adhérent "ARCHIVED"
  Quand le bibliothécaire tente de le modifier
  Alors l'action est refusée [RG-18]

# AC-019-5 — droits
Scénario: Adhérent non autorisé via la gestion
  Quand un utilisateur "MEMBER" tente de modifier un autre adhérent
  Alors l'action est refusée
```

### US-020 — Renouveler une adhésion · Priorité : MUST
**En tant que** bibliothécaire, **je veux** renouveler l'adhésion d'un adhérent, **afin qu'**il puisse continuer à emprunter.
**Critères d'acceptation**
```gherkin
# AC-020-1 — nominal
Scénario: Renouvellement anticipé
  Étant donné la date du jour 17/10/2026 et un adhérent "ACTIVE" expirant le 01/11/2026
  Quand le bibliothécaire renouvelle l'adhésion
  Alors la nouvelle date d'expiration est le 01/11/2027 [RG-15]

# AC-020-2 — nominal
Scénario: Renouvellement après expiration
  Étant donné la date du jour 17/10/2026 et un adhérent "EXPIRED" depuis le 01/09/2026
  Quand le bibliothécaire renouvelle l'adhésion
  Alors la nouvelle date d'expiration est le 17/10/2027 et le statut devient "ACTIVE" [RG-15]

# AC-020-3 — limite
Scénario: Renouvellement trop tôt
  Étant donné un adhérent dont l'expiration est dans plus de [RG-15] N jours
  Quand le bibliothécaire tente de renouveler
  Alors le renouvellement est refusé avec le motif « renouvellement possible à partir du <date> »

# AC-020-4 — erreur
Scénario: Adhérent suspendu ou archivé
  Étant donné un adhérent "SUSPENDED" ou "ARCHIVED"
  Quand le bibliothécaire tente de renouveler
  Alors le renouvellement est refusé ; un adhérent suspendu doit d'abord être réactivé

# AC-020-5 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de renouveler son adhésion lui-même
  Alors l'action est refusée
```

### US-021 — Suspendre ou réactiver un adhérent · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** suspendre un adhérent (comportement, litige), **afin de** bloquer ses emprunts et réservations jusqu'à régularisation.
**Critères d'acceptation**
```gherkin
# AC-021-1 — nominal
Scénario: Suspension
  Étant donné un adhérent "ACTIVE"
  Quand le bibliothécaire le suspend avec le motif "Litige sur document abîmé"
  Alors son statut devient "SUSPENDED"
  Et il ne peut plus emprunter, prolonger ni réserver [RG-16]
  Et ses réservations en cours sont conservées

# AC-021-2 — nominal
Scénario: Réactivation d'un adhérent dont l'adhésion est encore valide
  Étant donné un adhérent "SUSPENDED" dont la date d'expiration est future
  Quand le bibliothécaire le réactive
  Alors son statut devient "ACTIVE"

# AC-021-3 — limite
Scénario: Réactivation après la date d'expiration
  Étant donné un adhérent "SUSPENDED" dont la date d'expiration est passée
  Quand le bibliothécaire le réactive
  Alors son statut devient "EXPIRED" [RG-16]

# AC-021-4 — erreur
Scénario: Suspension sans motif
  Quand le bibliothécaire suspend un adhérent sans motif
  Alors la suspension est refusée [RG-17]

# AC-021-5 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de suspendre un adhérent
  Alors l'action est refusée
```

### US-022 — Archiver un adhérent · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** archiver les adhérents partis ou inactifs depuis longtemps, **afin de** respecter les durées de conservation du RGPD.
**Critères d'acceptation**
```gherkin
# AC-022-1 — nominal
Scénario: Archivage d'un adhérent sans dette
  Étant donné un adhérent "EXPIRED" sans emprunt en cours, réservation active ni pénalité impayée
  Quand le bibliothécaire l'archive et confirme le caractère irréversible
  Alors son statut devient "ARCHIVED", ses données personnelles sont effacées selon [RG-18]
  Et son compte "MEMBER" passe "DISABLED"

# AC-022-2 — nominal
Scénario: Liste des adhérents à archiver
  Étant donné 2 adhérents "EXPIRED" depuis plus de [RG-19] D1, dont 1 avec une pénalité "UNPAID"
  Quand le bibliothécaire consulte la liste des adhérents à archiver
  Alors les 2 adhérents sont listés et celui avec la pénalité est signalé comme non archivable

# AC-022-3 — erreur
Scénario: Adhérent avec emprunt en cours
  Étant donné un adhérent ayant 1 emprunt "ACTIVE"
  Quand le bibliothécaire tente de l'archiver
  Alors l'archivage est refusé et le motif bloquant est indiqué [RG-18]

# AC-022-4 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente d'archiver un adhérent
  Alors l'action est refusée
```

### US-023 — Consulter mon espace adhérent · Priorité : MUST
**En tant qu'**adhérent, **je veux** voir mes emprunts, réservations et pénalités, **afin de** connaître mes échéances sans me déplacer.
**Critères d'acceptation**
```gherkin
# AC-023-1 — nominal
Scénario: Vue de ma situation
  Étant donné un adhérent connecté avec 2 emprunts en cours, 1 réservation "PENDING" en position 2 et 0,60 € de pénalités "UNPAID"
  Quand il ouvre son espace
  Alors il voit pour chaque emprunt : titre, date d'emprunt, date de retour prévue, nombre de jours restants ou de retard
  Et pour chaque emprunt prolongeable, la possibilité de le prolonger (US-027)
  Et sa réservation avec sa position dans la file
  Et le détail et le total de ses pénalités
  Et sa date d'expiration d'adhésion

# AC-023-2 — nominal
Scénario: Historique limité
  Étant donné un emprunt rendu il y a plus de [RG-19] D2
  Quand l'adhérent consulte son historique
  Alors cet emprunt n'y figure pas

# AC-023-3 — vide
Scénario: Aucun emprunt
  Étant donné un adhérent sans emprunt, réservation ni pénalité
  Quand il ouvre son espace
  Alors un message indique qu'il n'a aucun document en cours

# AC-023-4 — droits
Scénario: Données d'un autre adhérent
  Quand un adhérent tente d'accéder aux emprunts, réservations ou pénalités d'un autre adhérent
  Alors l'accès est refusé et aucune donnée n'est transmise

# AC-023-5 — statut
Scénario: Adhésion expirée
  Étant donné un adhérent "EXPIRED"
  Quand il ouvre son espace
  Alors il voit sa situation et un message l'invitant à renouveler son adhésion au guichet [RG-16]
```

### US-024 — Modifier mes coordonnées · Priorité : COULD
**En tant qu'**adhérent, **je veux** mettre à jour mon téléphone et mon adresse postale, **afin que** la bibliothèque puisse me contacter.
**Critères d'acceptation**
```gherkin
# AC-024-1 — nominal
Scénario: Mise à jour du téléphone
  Étant donné un adhérent connecté
  Quand il modifie son téléphone et son adresse
  Alors les modifications sont enregistrées et journalisées

# AC-024-2 — erreur
Scénario: Champs réservés au personnel
  Quand l'adhérent tente de modifier son nom, sa date de naissance, son e-mail ou sa date d'expiration
  Alors la modification est refusée ; ces informations se modifient au guichet

# AC-024-3 — erreur
Scénario: Code postal invalide
  Quand l'adhérent saisit le code postal "750"
  Alors la modification est refusée
```

## 7. Exigences non fonctionnelles
| ID | Catégorie | Exigence vérifiable |
|---|---|---|
| NFR-06 | Sécurité | Un adhérent n'accède qu'à ses propres données (AC-018-5, AC-023-4). |
| NFR-07 | RGPD | Finalité, accès et durées de conservation conformes à §4 et [RG-18] [RG-19] ; l'archivage est irréversible et vérifiable (aucune donnée personnelle restante). |
| NFR-08 | Audit | Inscription, modification, renouvellement, suspension, réactivation et archivage journalisés. |
| NFR-03 | Accessibilité | L'espace adhérent est utilisable sur un écran de 360 px de large et au lecteur d'écran. |

## 8. Matrice de traçabilité
| US | AC | RG | Priorité |
|---|---|---|---|
| US-016 | AC-016-1 → AC-016-8 | RG-01, RG-04, RG-05, RG-13, RG-14, RG-15, RG-20 | MUST |
| US-017 | AC-017-1 → AC-017-5 | RG-13, RG-18 | MUST |
| US-018 | AC-018-1 → AC-018-5 | RG-15, RG-22, RG-23 | MUST |
| US-019 | AC-019-1 → AC-019-5 | RG-13, RG-14, RG-18 | MUST |
| US-020 | AC-020-1 → AC-020-5 | RG-15, RG-16 | MUST |
| US-021 | AC-021-1 → AC-021-5 | RG-16, RG-17 | SHOULD |
| US-022 | AC-022-1 → AC-022-4 | RG-18, RG-19 | SHOULD |
| US-023 | AC-023-1 → AC-023-5 | RG-16, RG-19 | MUST |
| US-024 | AC-024-1 → AC-024-3 | — | COULD |

## 9. Questions ouvertes
| ID | Question | Hypothèse retenue | Impact si faux |
|---|---|---|---|
| Q-05 | Quelles règles pour les mineurs ? | Représentant légal obligatoire sous 15 ans [RG-20] ; pas de restriction de catalogue par âge | Ajout de règles d'accès par âge |
| Q-06 | L'adhésion est-elle payante ? Quelle durée ? | Gratuite, 12 mois, renouvelable 30 jours avant l'échéance [RG-15] | Ajout d'une gestion de cotisation et d'encaissement |
| Q-07 | Tous les adhérents ont-ils un e-mail ? | Oui, e-mail obligatoire (identifiant de connexion) [RG-14] | Il faudrait un autre identifiant de connexion (numéro d'adhérent) et des adhérents sans compte |
| Q-08 | Durées de conservation validées par le délégué à la protection des données ? | 3 ans après expiration, historique d'emprunt 4 mois après retour [RG-19] | Ajustement de valeurs ; éventuel archivage automatique |

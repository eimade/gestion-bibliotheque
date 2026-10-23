---
feature: catalogue
agent: spec-kit-ba
version: 1
statut: READY
sources: [besoin utilisateur (conversation du 2026-10-17), .claude/agents/1-spec-kit-ba.md, docs/specs/INDEX.md, docs/specs/SPEC-authentification.md]
---
# SPEC — Catalogue et exemplaires

## 1. Objectif métier
Décrire l'intégralité du fonds de la bibliothèque (notices bibliographiques et exemplaires physiques) et permettre à chacun, connecté ou non, de savoir en quelques secondes si un livre est présent et disponible. Le catalogue est la référence sur laquelle s'appuient emprunts et réservations.
**Indicateur de succès** : 100 % des exemplaires physiques inventoriés dans l'application ; ≥ 80 % des recherches du public aboutissent à la consultation d'une notice (mesuré sur 1 mois).

## 2. Périmètre
- **Inclus** : recherche et consultation publiques ; création, modification, suppression de notices ; gestion des auteurs et des catégories ; ajout, modification et retrait d'exemplaires ; pré-remplissage d'une notice à partir de l'ISBN (COULD).
- **Exclus** : autres types de documents que les livres (DVD, revues, ressources numériques) ; images de couverture ; import/export au format bibliographique normalisé ; gestion des acquisitions (commandes, fournisseurs, budget) ; multi-sites.

## 3. Acteurs et rôles
| Acteur | Rôle applicatif | Description |
|---|---|---|
| Visiteur | aucun | Recherche et consulte les notices publiques [Q-03]. |
| Adhérent | `MEMBER` | Mêmes droits que le visiteur, plus l'accès à la réservation (SPEC-reservations). |
| Bibliothécaire | `LIBRARIAN` | Gère notices, auteurs, catégories et exemplaires. |
| Administrateur | `ADMIN` | Mêmes droits que le bibliothécaire. |

## 4. Entités métier
### ENT-Livre (`Book`) — notice bibliographique
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| ISBN | Numéro international normalisé du livre | Non | Format et unicité [RG-07] |
| Titre | Titre principal | Oui | 1 à 255 caractères [RG-08] |
| Sous-titre | Complément de titre | Non | 255 caractères max |
| Auteurs | Un ou plusieurs ENT-Auteur, ordonnés | Oui | ≥ 1 [RG-08] |
| Catégories | Une ou plusieurs ENT-Categorie | Oui | ≥ 1 [RG-08] |
| Éditeur | Maison d'édition | Non | 150 caractères max |
| Année de publication | Année de l'édition | Non | Bornes [RG-08] |
| Langue | Langue du texte | Oui | Valeur par défaut : français |
| Nombre de pages | — | Non | Entier > 0 |
| Résumé | Texte de présentation | Non | 4 000 caractères max |

### ENT-Auteur (`Author`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Nom | Nom de famille ou nom d'usage | Oui | 1 à 100 caractères |
| Prénom | — | Non | 100 caractères max |

### ENT-Categorie (`Category`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Libellé | Ex. « Roman », « Jeunesse », « Histoire » | Oui | [RG-12] |

### ENT-Exemplaire (`Copy`)
| Attribut | Description | Obligatoire | Contraintes métier |
|---|---|---|---|
| Numéro d'exemplaire | Code-barres collé sur le livre | Oui | [RG-09] |
| Notice | ENT-Livre dont c'est un exemplaire | Oui | Non modifiable |
| Cote | Emplacement de rangement (ex. « R DUM ») | Oui | 1 à 30 caractères |
| État physique | `GOOD`, `WORN`, `DAMAGED` | Oui | Défaut : `GOOD` |
| Date d'acquisition | — | Oui | ≤ date du jour ; défaut : date du jour |
| Statut | Voir cycle de vie | Oui | — |
| Motif de retrait | Raison du retrait du fonds | Si `WITHDRAWN` | [RG-10] |

**Cycle de vie de l'exemplaire** : création → `AVAILABLE` · `AVAILABLE` → `ON_LOAN` (emprunt) · `ON_LOAN` → `AVAILABLE` ou `RESERVED` (retour) · `RESERVED` → `ON_LOAN` / `AVAILABLE` (retrait ou expiration de la réservation) · `ON_LOAN` → `LOST` (perte déclarée) · `LOST` → `AVAILABLE` (retrouvé) · `AVAILABLE`/`LOST` → `WITHDRAWN` (retrait du fonds, état final). Détail : [INDEX §5](INDEX.md). Les transitions liées à la circulation sont spécifiées dans SPEC-emprunts et SPEC-reservations.

## 5. Règles de gestion
| ID | Règle | Valeur | Source/Hypothèse |
|---|---|---|---|
| RG-07 | ISBN facultatif ; s'il est saisi : ISBN-13 ou ISBN-10 à clé de contrôle valide, ISBN-10 converti et enregistré en ISBN-13, tirets et espaces ignorés ; unique parmi les notices | 13 chiffres | Hypothèse Q-03 (fonds anciens sans ISBN) |
| RG-08 | Une notice a un titre, au moins 1 auteur et au moins 1 catégorie ; l'année de publication est comprise entre une borne basse et l'année en cours | Borne basse : 1450 | Règle métier |
| RG-09 | Le numéro d'exemplaire est attribué automatiquement à la création, unique, jamais modifié ni réattribué, même après retrait | 8 chiffres, séquentiel | Hypothèse : étiquettes code-barres imprimées depuis l'application |
| RG-10 | Une notice ne peut être supprimée que si elle n'a aucun exemplaire, quel que soit son statut. Un exemplaire n'est jamais supprimé : il est retiré (`WITHDRAWN`) avec un motif obligatoire, uniquement depuis `AVAILABLE` ou `LOST`. Le retrait est définitif | Motifs : `DAMAGED`, `LOST`, `WEEDED` (désherbage), `OTHER` | Règle métier (traçabilité de l'historique d'emprunt) |
| RG-11 | Recherche publique : mot-clé appliqué au titre, sous-titre, nom des auteurs et ISBN, insensible à la casse et aux accents, longueur minimale du mot-clé ; seules les notices ayant au moins 1 exemplaire hors `WITHDRAWN` sont visibles du public ; « disponibles » = nombre d'exemplaires `AVAILABLE` ; « total » = nombre d'exemplaires hors `WITHDRAWN` et `LOST` | Mot-clé ≥ 2 caractères | Règle métier |
| RG-12 | Libellé de catégorie unique (insensible à la casse et aux accents), de longueur bornée ; une catégorie utilisée par au moins 1 notice ne peut pas être supprimée | 2 à 50 caractères | Règle métier |

## 6. User stories

### US-007 — Rechercher un livre dans le catalogue · Priorité : MUST
**En tant que** visiteur, **je veux** rechercher un livre par mot-clé et le filtrer, **afin de** savoir si la bibliothèque le possède.
**Critères d'acceptation**
```gherkin
# AC-007-1 — nominal
Scénario: Recherche par titre
  Étant donné une notice "Le Comte de Monte-Cristo" d'Alexandre Dumas avec 2 exemplaires dont 1 "AVAILABLE"
  Quand un visiteur recherche "monte cristo"
  Alors la notice apparaît dans les résultats avec le titre, les auteurs, l'année, "1 disponible sur 2" [RG-11]

# AC-007-2 — nominal
Scénario: Recherche par auteur sans accents
  Étant donné une notice dont l'auteur est "Émile Zola"
  Quand un visiteur recherche "emile zola"
  Alors la notice apparaît dans les résultats [RG-11]

# AC-007-3 — nominal
Scénario: Recherche par ISBN avec tirets
  Étant donné une notice d'ISBN "9782070360024"
  Quand un visiteur recherche "978-2-07-036002-4"
  Alors cette notice est le seul résultat

# AC-007-4 — filtres
Scénario: Filtrer par catégorie et disponibilité
  Étant donné des notices des catégories "Roman" et "Histoire", certaines sans exemplaire "AVAILABLE"
  Quand un visiteur filtre sur la catégorie "Roman" et « disponibles uniquement »
  Alors seules les notices de catégorie "Roman" ayant au moins 1 exemplaire "AVAILABLE" sont affichées

# AC-007-5 — limite
Scénario: Mot-clé trop court
  Quand un visiteur recherche un mot-clé de 1 caractère [RG-11]
  Alors la recherche n'est pas lancée et la longueur minimale est indiquée

# AC-007-6 — vide
Scénario: Aucun résultat
  Quand un visiteur recherche "zzzzqqq"
  Alors un message indique qu'aucun livre ne correspond à la recherche

# AC-007-7 — visibilité
Scénario: Notice entièrement retirée
  Étant donné une notice dont tous les exemplaires sont "WITHDRAWN"
  Quand un visiteur la recherche par son titre exact
  Alors elle n'apparaît pas dans les résultats [RG-11]
  Mais elle apparaît dans la recherche d'un bibliothécaire avec la mention « retirée »
```

### US-008 — Consulter la fiche d'un livre · Priorité : MUST
**En tant que** visiteur, **je veux** consulter la fiche détaillée d'un livre et l'état de ses exemplaires, **afin de** savoir où le trouver et quand il sera disponible.
**Critères d'acceptation**
```gherkin
# AC-008-1 — nominal
Scénario: Fiche publique
  Étant donné une notice avec 3 exemplaires : 1 "AVAILABLE", 1 "ON_LOAN" à rendre le 05/11/2026, 1 "WITHDRAWN"
  Quand un visiteur ouvre la fiche
  Alors il voit toutes les informations de la notice
  Et 2 exemplaires avec leur cote et leur statut : « disponible » ; « emprunté — retour prévu le 05/11/2026 »
  Et l'exemplaire "WITHDRAWN" n'est pas affiché
  Et aucune information sur l'emprunteur n'est affichée

# AC-008-2 — nominal
Scénario: Fiche vue par le personnel
  Étant donné la même notice
  Quand un bibliothécaire ouvre la fiche
  Alors il voit les 3 exemplaires avec numéro, cote, état physique, date d'acquisition et statut, y compris "WITHDRAWN" et son motif

# AC-008-3 — erreur
Scénario: Notice inexistante
  Quand un visiteur demande une notice qui n'existe pas
  Alors un message indique que le livre est introuvable
```

### US-009 — Créer une notice · Priorité : MUST
**En tant que** bibliothécaire, **je veux** créer la notice d'un nouveau livre, **afin de** pouvoir y rattacher ses exemplaires.
**Critères d'acceptation**
```gherkin
# AC-009-1 — nominal
Scénario: Création complète
  Étant donné un bibliothécaire connecté
  Quand il saisit un titre, l'ISBN-13 valide "9782070360024", l'auteur existant "Albert Camus", la catégorie "Roman" et l'année 1942
  Alors la notice est créée sans exemplaire
  Et elle n'est pas encore visible du public [RG-11]

# AC-009-2 — nominal
Scénario: Création d'un nouvel auteur à la volée
  Quand le bibliothécaire saisit un auteur qui n'existe pas encore
  Alors l'auteur est créé et rattaché à la notice

# AC-009-3 — nominal
Scénario: ISBN-10 converti
  Quand le bibliothécaire saisit l'ISBN-10 valide "2070360024"
  Alors la notice est enregistrée avec l'ISBN-13 "9782070360024" [RG-07]

# AC-009-4 — erreur
Scénario: ISBN invalide
  Quand le bibliothécaire saisit l'ISBN "9782070360025" dont la clé de contrôle est fausse
  Alors la création est refusée avec le motif « ISBN invalide » [RG-07]

# AC-009-5 — erreur
Scénario: ISBN déjà présent
  Étant donné une notice existante d'ISBN "9782070360024"
  Quand le bibliothécaire crée une notice avec le même ISBN
  Alors la création est refusée et la notice existante est indiquée [RG-07]

# AC-009-6 — erreur
Scénario: Données obligatoires manquantes
  Quand le bibliothécaire valide sans titre, sans auteur ou sans catégorie
  Alors la création est refusée et chaque champ manquant est signalé [RG-08]

# AC-009-7 — limite
Scénario: Année de publication hors bornes
  Quand le bibliothécaire saisit une année supérieure à l'année en cours ou inférieure à la borne [RG-08]
  Alors la création est refusée

# AC-009-8 — droits
Scénario: Adhérent non autorisé
  Étant donné un utilisateur "MEMBER" ou un visiteur
  Quand il tente de créer une notice
  Alors l'action est refusée
```

### US-010 — Modifier une notice · Priorité : MUST
**En tant que** bibliothécaire, **je veux** corriger ou compléter une notice, **afin que** le catalogue reste exact.
**Critères d'acceptation**
```gherkin
# AC-010-1 — nominal
Scénario: Modification
  Étant donné une notice existante
  Quand le bibliothécaire modifie le résumé et ajoute une catégorie
  Alors les modifications sont enregistrées et visibles immédiatement dans le catalogue public
  Et la modification est journalisée

# AC-010-2 — erreur
Scénario: Retrait du dernier auteur
  Quand le bibliothécaire retire l'unique auteur de la notice
  Alors l'enregistrement est refusé [RG-08]

# AC-010-3 — erreur
Scénario: Modification concurrente
  Étant donné deux bibliothécaires ayant ouvert la même notice
  Quand le second enregistre après le premier sans avoir rechargé la notice
  Alors son enregistrement est refusé avec le motif « la notice a été modifiée entre-temps »
  Et aucune modification du premier n'est perdue

# AC-010-4 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de modifier une notice
  Alors l'action est refusée
```

### US-011 — Supprimer une notice · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** supprimer une notice créée par erreur, **afin de** ne pas polluer le catalogue.
**Critères d'acceptation**
```gherkin
# AC-011-1 — nominal
Scénario: Suppression d'une notice sans exemplaire
  Étant donné une notice sans aucun exemplaire
  Quand le bibliothécaire la supprime et confirme
  Alors la notice n'existe plus
  Et les auteurs restent disponibles pour d'autres notices

# AC-011-2 — erreur
Scénario: Notice ayant des exemplaires
  Étant donné une notice ayant 1 exemplaire au statut "WITHDRAWN"
  Quand le bibliothécaire tente de la supprimer
  Alors la suppression est refusée avec le motif « la notice possède des exemplaires » [RG-10]

# AC-011-3 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de supprimer une notice
  Alors l'action est refusée
```

### US-012 — Ajouter des exemplaires à une notice · Priorité : MUST
**En tant que** bibliothécaire, **je veux** enregistrer les exemplaires physiques reçus pour une notice, **afin qu'**ils puissent être prêtés.
**Critères d'acceptation**
```gherkin
# AC-012-1 — nominal
Scénario: Ajout de plusieurs exemplaires
  Étant donné une notice existante
  Quand le bibliothécaire ajoute 3 exemplaires avec la cote "R CAM", l'état "GOOD" et la date d'acquisition du jour
  Alors 3 exemplaires sont créés au statut "AVAILABLE", chacun avec un numéro distinct [RG-09]
  Et la notice devient visible du public si elle ne l'était pas [RG-11]

# AC-012-2 — nominal
Scénario: Réservation en attente
  Étant donné une notice ayant 1 réservation "PENDING" (SPEC-reservations)
  Quand le bibliothécaire ajoute 1 exemplaire
  Alors cet exemplaire est mis de côté pour la première réservation de la file comme lors d'un retour [RG-32]

# AC-012-3 — erreur
Scénario: Cote manquante
  Quand le bibliothécaire ajoute un exemplaire sans cote
  Alors l'ajout est refusé

# AC-012-4 — erreur
Scénario: Date d'acquisition future
  Quand le bibliothécaire saisit une date d'acquisition postérieure à la date du jour
  Alors l'ajout est refusé

# AC-012-5 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente d'ajouter un exemplaire
  Alors l'action est refusée
```

### US-013 — Modifier ou retirer un exemplaire · Priorité : MUST
**En tant que** bibliothécaire, **je veux** mettre à jour la cote ou l'état d'un exemplaire, ou le retirer du fonds, **afin que** l'inventaire corresponde à la réalité des rayons.
**Critères d'acceptation**
```gherkin
# AC-013-1 — nominal
Scénario: Modification de l'état physique
  Étant donné un exemplaire "AVAILABLE" en état "GOOD"
  Quand le bibliothécaire passe son état à "DAMAGED" et sa cote à "R CAM 2"
  Alors les nouvelles valeurs sont enregistrées et le numéro d'exemplaire reste inchangé [RG-09]

# AC-013-2 — nominal
Scénario: Retrait d'un exemplaire disponible
  Étant donné un exemplaire "AVAILABLE"
  Quand le bibliothécaire le retire avec le motif "WEEDED" et confirme
  Alors l'exemplaire passe au statut "WITHDRAWN" [RG-10]
  Et il n'est plus comptabilisé dans le total ni dans les disponibles [RG-11]

# AC-013-3 — erreur
Scénario: Retrait d'un exemplaire emprunté
  Étant donné un exemplaire "ON_LOAN" ou "RESERVED"
  Quand le bibliothécaire tente de le retirer
  Alors le retrait est refusé avec le motif « exemplaire en circulation » [RG-10]

# AC-013-4 — erreur
Scénario: Retrait sans motif
  Quand le bibliothécaire tente de retirer un exemplaire sans motif
  Alors le retrait est refusé [RG-10]

# AC-013-5 — erreur
Scénario: Exemplaire retiré non modifiable
  Étant donné un exemplaire "WITHDRAWN"
  Quand le bibliothécaire tente de changer son statut ou sa cote
  Alors l'action est refusée [RG-10]

# AC-013-6 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de modifier un exemplaire
  Alors l'action est refusée
```

### US-014 — Gérer les catégories · Priorité : SHOULD
**En tant que** bibliothécaire, **je veux** créer, renommer et supprimer des catégories, **afin d'**organiser le classement du catalogue.
**Critères d'acceptation**
```gherkin
# AC-014-1 — nominal
Scénario: Création et renommage
  Quand le bibliothécaire crée la catégorie "Bande dessinée" puis la renomme "BD"
  Alors la catégorie "BD" est proposée lors de la saisie des notices et dans les filtres de recherche

# AC-014-2 — erreur
Scénario: Doublon de libellé
  Étant donné la catégorie "Roman"
  Quand le bibliothécaire crée la catégorie "roman"
  Alors la création est refusée avec le motif « catégorie déjà existante » [RG-12]

# AC-014-3 — erreur
Scénario: Suppression d'une catégorie utilisée
  Étant donné la catégorie "Roman" utilisée par 1 notice
  Quand le bibliothécaire tente de la supprimer
  Alors la suppression est refusée et le nombre de notices concernées est indiqué [RG-12]

# AC-014-4 — limite
Scénario: Longueur du libellé
  Quand le bibliothécaire saisit un libellé de 1 caractère ou de 51 caractères [RG-12]
  Alors l'enregistrement est refusé

# AC-014-5 — droits
Scénario: Adhérent non autorisé
  Quand un utilisateur "MEMBER" tente de créer une catégorie
  Alors l'action est refusée
```

### US-015 — Pré-remplir une notice à partir de l'ISBN · Priorité : COULD
**En tant que** bibliothécaire, **je veux** que la notice soit pré-remplie à partir de l'ISBN saisi, **afin de** cataloguer plus vite et sans faute de frappe.
**Critères d'acceptation**
```gherkin
# AC-015-1 — nominal
Scénario: ISBN connu de la source bibliographique
  Étant donné un ISBN valide connu de la source bibliographique externe [Q-04]
  Quand le bibliothécaire demande le pré-remplissage
  Alors le titre, les auteurs, l'éditeur, l'année, la langue et le nombre de pages sont proposés
  Et le bibliothécaire peut les modifier avant de valider la création (US-009)

# AC-015-2 — erreur
Scénario: ISBN inconnu ou source indisponible
  Quand la source ne connaît pas l'ISBN ou ne répond pas
  Alors un message l'indique et la saisie manuelle reste possible sans perte des champs déjà saisis
```

## 7. Exigences non fonctionnelles
| ID | Catégorie | Exigence vérifiable |
|---|---|---|
| NFR-01 | Performance | Première page de résultats en ≤ 2 s (95 % des requêtes) sur 50 000 notices et 100 utilisateurs simultanés. |
| NFR-03 | Accessibilité | Recherche et fiche utilisables au clavier et avec un lecteur d'écran ; la disponibilité n'est pas indiquée uniquement par une couleur. |
| NFR-08 | Audit | Création, modification, suppression de notice et retrait d'exemplaire journalisés. |
| NFR-10 | Fiabilité | Deux modifications simultanées d'une même notice ne s'écrasent pas silencieusement (AC-010-3). |

## 8. Matrice de traçabilité
| US | AC | RG | Priorité |
|---|---|---|---|
| US-007 | AC-007-1 → AC-007-7 | RG-07, RG-11 | MUST |
| US-008 | AC-008-1 → AC-008-3 | RG-10, RG-11 | MUST |
| US-009 | AC-009-1 → AC-009-8 | RG-07, RG-08, RG-11 | MUST |
| US-010 | AC-010-1 → AC-010-4 | RG-08 | MUST |
| US-011 | AC-011-1 → AC-011-3 | RG-10 | SHOULD |
| US-012 | AC-012-1 → AC-012-5 | RG-09, RG-11, RG-32 | MUST |
| US-013 | AC-013-1 → AC-013-6 | RG-09, RG-10, RG-11 | MUST |
| US-014 | AC-014-1 → AC-014-5 | RG-12 | SHOULD |
| US-015 | AC-015-1 → AC-015-2 | RG-07 | COULD |

## 9. Questions ouvertes
| ID | Question | Hypothèse retenue | Impact si faux |
|---|---|---|---|
| Q-03 | Le catalogue est-il consultable sans connexion ? L'ISBN est-il obligatoire ? | Oui, catalogue public sans connexion ; ISBN facultatif (fonds anciens) mais unique s'il est saisi | Si catalogue privé : US-007/US-008 réservées aux connectés. Si ISBN obligatoire : RG-07 modifiée |
| Q-04 | Quelle source bibliographique externe utiliser pour US-015 ? | À choisir par l'Architecte parmi des sources publiques gratuites ; US-015 réalisable en dernier | US-015 reportée si aucune source acceptable |

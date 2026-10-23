---
feature: catalogue
agent: developpeur-fullstack
version: 1
statut: READY
sources: [docs/specs/SPEC-catalogue.md, docs/design/UX-catalogue.md, docs/design/UX-socle.md, docs/architecture/ARCH-catalogue.md, docs/architecture/openapi.yaml, docs/dev/DEV-socle.md]
---
# DEV — Catalogue (notices, exemplaires, catégories)

## 1. Résumé
Mode **IMPLÉMENTATION**, itération 1. Le périmètre d'ARCH-catalogue §1 est livré de bout en bout : US-007 à US-014, avec les endpoints `/catalog/**` publics et `/books`, `/authors`, `/copies`, `/categories` réservés au personnel, ainsi que les écrans ECR-08 à ECR-15. **Restent hors périmètre :** US-015, et les fonctions de circulation et de réservation présentes dans ECR-09 et ECR-12 (Q-38).

**Changement de navigation :** l'accueil public est désormais le catalogue. `/` redirige vers `/catalogue`, qui est aussi la destination après déconnexion et le lien d'accueil pour un visiteur. Cela clôt la partie « accueil public » de Q-33.

## 2. Fichiers créés / modifiés
| Fichier | Type | Rôle | Réf. ARCH |
|---|---|---|---|
| `backend/…/common/audit/ActorLabelResolver.java`, `AuditService` (`recordAs`) | création/modif | Libellé de l'auteur d'une action sans dépendre du module `auth` (ADR-001) | common |
| `backend/…/auth/service/AccountActorLabelResolver.java` | création | Implémentation du port ; renvoie « Compte inconnu » si l'identifiant est absent | service |
| `backend/…/common/web/LikePatterns` (`startsWith`), `GlobalExceptionHandler`, `SecurityConfig`, `application.yml` | modif | Contraintes `uk_books_isbn` / `uk_categories_label` traduites en codes métier ; règles d'accès des nouvelles familles d'URL ; `default_batch_fetch_size` | common |
| `backend/…/catalog/domain/*` (10 types) | création | `Book` (auteurs ordonnés par `@OrderColumn`), `Copy` (transitions `update` et `withdraw`), `Author`, `Category`, `Language`, énumérations | domain |
| `backend/…/catalog/repository/*` | création | `BookSpecifications` (mots-clés découpés, ISBN, filtres, tri par pertinence), décomptes groupés `CopyCounts`, `CopyNumberGenerator` (séquence) | repository |
| `backend/…/circulation/repository/CopyLoanInfoJdbcAdapter.java` | création | Port `CopyLoanInfo` : date de retour prévue des emprunts en cours | repository |
| `backend/…/catalog/service/*` | création | `CatalogSearchService`, `BookService`, `CopyService`, `CategoryService`, `AuthorService`, `Isbn`, `Validation`, `CatalogViews` | service |
| `backend/…/catalog/web/*` | création | 5 contrôleurs + `CatalogRequests` (records validés) | web |
| `backend/src/test/java/…/catalog/**` (10 classes, 79 tests), `AccountActorLabelResolverTest` (2 tests), `support/SecurityRequests` | création | Tests unitaires des services, des spécifications et des contrôleurs | — |
| `frontend/src/app/shared/{models/statuses, pipes/date-fr.pipe, validators/isbn.validator, data/view-state}.ts`, `shared/ui/search-input` | création/modif | Statuts d'exemplaire, dates `JJ/MM/AAAA`, ISBN (RG-07), état de lecture générique `viewState` | shared |
| `frontend/src/app/features/catalogue/data-access/*` (7 fichiers) | création | Services HTTP typés, modèles miroir, messages exacts de l'UX | data-access |
| `frontend/src/app/features/catalogue/components/*` (9 composants) | création | `availability-badge`, `book-result-card`, `book-summary`, `author-picker`, `category-picker`, `book-form`, `add-copies-dialog` (ECR-13), `copy-edit-dialog` (ECR-14) | components |
| `frontend/src/app/features/catalogue/pages/*` (6 pages) | création | ECR-08, ECR-09, ECR-10, ECR-11, ECR-12, ECR-15 | pages |
| `frontend/src/app/app.routes.ts`, `core/layout/navigation.ts`, `core/auth/sign-out.service.ts`, `core/errors/error-pages.ts`, `core/layout/app-header.component.ts` | modif | Routes publiques et de gestion, menus « Catalogue » et « Catégories », accueil public `/catalogue` | — |
| `frontend/src/app/**/*.spec.ts` (4 fichiers, 21 tests) | création | ISBN, dates, ECR-12, ECR-15 | — |
| `docs/architecture/ARCH-catalogue.md`, `openapi.yaml` | création/modif | Contrat produit en rôle Architecte avant le code (Redocly : valide) | — |

## 3. Traçabilité
| US | Endpoint(s) | Écran / composant | Tests |
|---|---|---|---|
| US-007 (recherche publique) | `GET /catalog/books`, `/catalog/categories`, `/catalog/languages` | ECR-08 `catalog-search.page`, `book-result-card` | `CatalogSearchServiceTest`, `BookSpecificationsTest`, `CatalogControllerTest` |
| US-008 (fiche publique et fiche de gestion) | `GET /catalog/books/{id}`, `GET /books/{id}` | ECR-09, ECR-12 | `CatalogSearchServiceTest` (notice entièrement retirée → 404), `book-admin-detail.page.spec` |
| US-009 (création de notice) | `POST /books`, `GET /authors`, `POST /authors` | ECR-11 `book-form`, `author-picker`, `category-picker` | `BookServiceTest`, `AuthorServiceTest`, `CatalogManagementControllerTest`, `isbn.validator.spec` |
| US-010 (modification) | `PUT /books/{id}` | ECR-11 (conflit → rechargement) | `BookServiceTest` (version, `CONCURRENT_MODIFICATION`) |
| US-011 (suppression) | `DELETE /books/{id}` | ECR-12 (bouton désactivé avec la raison) | `BookServiceTest` (`BOOK_HAS_COPIES`), `book-admin-detail.page.spec` |
| US-012 (ajout d'exemplaires) | `POST /books/{id}/copies` | ECR-13 | `CopyServiceTest`, `book-admin-detail.page.spec` (numéros annoncés) |
| US-013 (modification et retrait) | `PUT /copies/{n}`, `POST /copies/{n}/withdrawal` | ECR-14 (motif obligatoire, confirmation) | `CopyServiceTest` (`COPY_IN_CIRCULATION`, `COPY_WITHDRAWN`), `book-admin-detail.page.spec` |
| US-014 (catégories) | `GET/POST /categories`, `PUT/DELETE /categories/{id}` | ECR-15 | `CategoryServiceTest` (`CATEGORY_IN_USE` avec décompte), `categories.page.spec` |

## 4. Résultats de build et tests
| Commande | Résultat | Détails |
|---|---|---|
| `./gradlew clean build` | BUILD SUCCESSFUL | 201 tests au total ; JaCoCo `catalog.service` à 98 %, `auth.service` à 100 % ; ArchUnit conforme |
| `npx ng build` (production) | réussi | lots chargés à la demande par page du catalogue |
| `npx ng test --watch=false` | 89 / 89 | 14 fichiers, dont 21 nouveaux tests pour le catalogue |
| Parcours d'API réel (pile Docker, session et CSRF) | 30 vérifications conformes | « monte cristo » trouve « Monte-Cristo », « emile zola » trouve Germinal, ISBN avec tirets, notice entièrement retirée masquée (404), `CATEGORY_IN_USE`, `CONCURRENT_MODIFICATION` |
| Captures Edge headless avec la CSP active | conformes | ECR-08 (mot-clé, filtre par catégorie) et ECR-09 ; le CSS s'applique |
| Écrans de gestion dans un vrai navigateur | **non fait** | Il faut se connecter en personnel ; le mot de passe de test de l'administrateur n'était pas disponible dans cette session. Couverts par les tests de composants : QA à faire avec Cypress |

## 5. Traitement des constats (mode CORRECTION)
N/A — première itération.

## 6. Écarts et questions
| ID | Écart / question | Hypothèse appliquée | Agent concerné |
|---|---|---|---|
| Q-37 | Tri par nombre d'exemplaires (ECR-10) | Non livré (ARCH-catalogue) | designer-ux |
| Q-38 | Circulation et réservation dans ECR-09 et ECR-12 | Reportées avec leurs features | — |
| Q-39 | Le sélecteur de catégories de l'UX prévoit l'autocomplétion | Cases à cocher (les catégories sont peu nombreuses) | designer-ux |
| — | Les vues de service (`CatalogViews`) servent directement de DTO de réponse | Pas de couche de correspondance distincte : les records sont immuables et sans entité JPA exposée | code-reviewer |
| — | Les 201 de création d'exemplaires, d'auteurs et de catégories ne portent pas d'en-tête `Location` | Conforme à `openapi.yaml` : aucune ressource unitaire `GET` n'existe pour eux | — |
| — | Remplacer les auteurs d'une notice augmente sa `version` de 2 | Deux `flush` sont nécessaires pour ne pas violer la clé (`book_id`, `author_order`) ; le client utilise toujours la version renvoyée | code-reviewer |

## 7. Points d'attention pour le Reviewer et le QA
- **Recherche** : 5 mots au plus. Chaque mot doit figurer dans le titre, le sous-titre ou un auteur ; le mot-clé peut aussi correspondre à l'ISBN. `LIKE` est échappé et la collation ignore la casse et les accents.
- **Visibilité publique** : une notice sans exemplaire, ou dont tous les exemplaires sont retirés, n'apparaît pas au public (404 sur la fiche).
- **Numéros d'exemplaire** : ils proviennent de la séquence `copy_number_seq`. À tester en intégration : deux ajouts simultanés ne doivent jamais produire de doublon.
- **`data-testid`** : ceux de l'UX sont respectés. Ajouts : `book-admin-delete-reason`, `book-delete-dialog|confirm|cancel`, `copy-edit-tab-edit|withdraw`, `copy-withdraw-blocked`, `copy-edit-readonly`, `categories-delete-blocked`, `nav-catalog`, `nav-categories`, `nav-member-catalog`.
- **Fonds de démonstration** présent dans la base locale : 4 catégories, 5 auteurs et 6 notices, dont une entièrement retirée.

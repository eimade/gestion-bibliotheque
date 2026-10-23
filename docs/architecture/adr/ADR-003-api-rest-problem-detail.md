# ADR-003 — API REST, erreurs ProblemDetail et pagination

**Statut** : Accepté (17/10/2026)

## Contexte
Le frontend (UX §7) a besoin de messages exacts par motif de refus, de refus à motifs multiples (AC-025-5) et de listes paginées partout.

## Décision
1. **Préfixe** `/api/v1` ; ressources au pluriel, en kebab-case ; identifiants fonctionnels dans les chemins quand ils existent (numéro d'adhérent `A000123`, numéro d'exemplaire `00001234`).
2. **Espace adhérent** sous `/api/v1/me/**` : l'adhérent est déduit de la session, **jamais d'un paramètre** (protection IDOR par construction, AC-023-4).
3. **Codes HTTP** : 200, 201 + `Location`, 204 ; 400 (format ou validation de champ) ; 401 (non authentifié, identifiants refusés) ; 403 (droits, mot de passe temporaire à changer) ; 404 ; **409** (conflit avec l'état courant : quota, statut, doublon, modification concurrente) ; **422** (donnée syntaxiquement valide mais refusée par une règle : politique de mot de passe, montant différent du total) ; 503 (source externe indisponible, US-015).
4. **Erreurs au format RFC 9457/7807 `ProblemDetail`** (`application/problem+json`) avec les propriétés :
   - `code` : code métier stable `UPPER_SNAKE_CASE` (catalogue complet en ARCH-socle §4) ;
   - `reasons` : liste de codes de motifs (refus multiples, ex. éligibilité) ;
   - `fieldErrors` : liste `{ field, code, message }` pour les erreurs de validation ;
   - propriétés contextuelles documentées par code (ex. `lockedUntil`, `renewableFrom`, `existingBook`).
   Le `detail` est un texte français lisible ; le frontend affiche en priorité son propre message associé à `code`.
5. **Pagination** : `?page=0&size=20&sort=champ,asc|desc` ; `size` ≤ 100 ; champs de tri validés par **liste blanche** par ressource ; réponse `PageResponse { content, page, size, totalElements, totalPages }`.
6. **Contrat** : `docs/architecture/openapi.yaml` (OpenAPI 3.1) est la source de vérité ; il est complété feature par feature (ARCH-<slug>.md). springdoc n'est activé qu'en profil `dev`.
7. **Concurrence** : les ressources modifiables exposent leur `version` ; une écriture avec une version périmée renvoie 409 `CONCURRENT_MODIFICATION`.
8. Dates au format ISO 8601 (`2026-11-07`, instants `2026-10-17T14:32:00Z`) ; montants en nombre décimal à 2 décimales (`1.00`).

## Alternatives écartées
- **Format d'erreur maison** : ProblemDetail est natif dans Spring 6 et standardisé.
- **Pagination par curseur** : inutile aux volumes visés ; l'UX affiche « Page x sur y ».
- **Numéros fonctionnels exclus des URL** (identifiants techniques seulement) : moins pratique au guichet (scan), sans gain de sécurité car les rôles sont contrôlés.

## Conséquences
- Chaque feature fournit sa table « code → message » côté frontend (`data-access/<feature>-messages.ts`, UX-socle §5.6).
- Le QA vérifie `code` et le statut HTTP, pas le texte de `detail`.

# Instructions pour Codex

## Vue d'ensemble

Ce dépôt contient une application de gestion de bibliothèque en français :

- `backend/` : Spring Boot 3, Java 21, Gradle Groovy, MariaDB et Flyway.
- `frontend/` : Angular 22 standalone, TypeScript strict, Tailwind CSS et Cypress.
- `db/` : scripts d'initialisation de l'utilisateur applicatif et de sauvegarde.
- `docs/` : spécifications métier, architecture, ADR, UX et guides de développement.
- `docker-compose.yml` : pile complète (MariaDB, backend, frontend, sauvegarde).
- `docker-compose.dev.yml` : exposition locale de MariaDB et du backend.

Lire les documents concernés dans `docs/` avant toute modification métier. Les ADR font foi
en cas de conflit avec une intuition d'implémentation.

## Règles de modification

- Préserver l'architecture modulaire du backend (`common`, `auth`, `catalog`, `member`,
  `circulation`, `reporting`) et l'injection par constructeur.
- Ne pas modifier `backend/build/`, `frontend/dist/`, `frontend/node_modules/` ou les autres
  artefacts générés.
- Les changements de schéma passent par une nouvelle migration Flyway dans
  `backend/src/main/resources/db/migration/`; ne pas éditer une migration déjà livrée.
- Ne jamais ajouter de secret réel. Utiliser `.env.example` pour documenter une variable et
  laisser `.env` hors du contrôle de version.
- Respecter les contrats de `docs/architecture/openapi.yaml` et les codes `ProblemDetail`.
- Garder les textes et libellés utilisateur en français, sauf contrainte technique.
- Utiliser `rg` pour rechercher du code et conserver les fins de ligne existantes.

## Commandes de validation

Depuis la racine :

```text
docker compose config -q
```

Backend :

```text
cd backend
./gradlew clean build
```

Sous Windows, utiliser `gradlew.bat clean build` si nécessaire. Les tests d'intégration
utilisent Testcontainers MariaDB et nécessitent Docker.

Frontend :

```text
cd frontend
npm ci
npm run build
npm run test:ci
```

Éviter de lancer une suite longue ou Docker si la demande porte seulement sur une lecture ou
une petite modification. Dans le compte rendu, indiquer les commandes réellement exécutées et
les éventuelles dépendances manquantes.

## Développement local

1. Copier `.env.example` vers `.env` et remplacer toutes les valeurs `a-changer`.
2. Lancer la base et le backend avec `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db`.
3. Lancer le backend avec le profil `dev` (port `8081`) puis le frontend avec `npm start`.
4. Le proxy Angular relaie `/api` vers `http://localhost:8081`.

Ne pas exposer la base ou le backend dans la configuration de production : seul le frontend
doit être publié par `docker-compose.yml`.

## Compte rendu attendu

À la fin d'une tâche, résumer les fichiers modifiés, le comportement changé, les validations
exécutées et les limites restantes. Ne pas présenter une commande comme réussie sans son résultat.

# Gestion de bibliothèque

Application web de gestion d'une bibliothèque municipale : catalogue, adhérents, emprunts et retours au guichet,
réservations, pénalités, statistiques et journal d'audit. Interface entièrement en français.

**Stack** : Java 21 · Spring Boot 3.5 · MariaDB · Flyway · Angular 22 (standalone) · Tailwind CSS · Docker Compose

## Fonctionnalités

| Profil | Ce qu'il peut faire |
|---|---|
| Public | Consulter le catalogue et la disponibilité des exemplaires |
| Adhérent | Suivre ses emprunts et échéances, réserver, prolonger |
| Bibliothécaire | Notices et exemplaires, inscriptions, guichets emprunt / retour (raccourcis clavier), réservations, encaissement des pénalités |
| Administrateur | Comptes du personnel, paramètres de prêt, remises gracieuses, statistiques, journal d'audit |

Règles notables : durée de prêt et plafonds paramétrables, pénalités de retard calculées automatiquement, mise de côté
des exemplaires réservés au retour, changement obligatoire du mot de passe provisoire, sessions serveur sécurisées.

## Démarrage rapide (Docker)

Prérequis : Docker Desktop.

```bash
cp .env.example .env        # puis remplacer toutes les valeurs « a-changer »
docker compose up -d --build
```

L'application est servie sur http://localhost:8080 (port modifiable avec `FRONTEND_PORT`). Le compte administrateur
initial est celui défini par `APP_BOOTSTRAP_ADMIN_EMAIL` / `APP_BOOTSTRAP_ADMIN_PASSWORD` dans `.env`.
Seul le frontend (nginx) est exposé ; il relaie `/api` vers le backend.

## Développement

```bash
# Base de données et backend exposés localement
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db

# Backend (profil dev, port 8081)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend (proxy /api vers http://localhost:8081)
cd frontend && npm ci && npm start
```

Validation :

```bash
cd backend && ./gradlew clean build          # tests unitaires + intégration (Testcontainers, Docker requis)
cd frontend && npm run build && npm run test:ci
```

## Organisation du dépôt

```
backend/    API Spring Boot, modules common · auth · catalog · member · circulation · reporting
frontend/   Application Angular, tests Vitest et Cypress
db/         Initialisation des utilisateurs MariaDB et sauvegardes
docs/       Spécifications, UX, architecture (OpenAPI, ADR), notes de développement, livrables PDF
```

Documents de référence : [cahier des charges](docs/livrables/Cahier-des-charges.pdf),
[dossier de conception](docs/livrables/Dossier-de-conception.pdf), [contrat d'API](docs/architecture/openapi.yaml),
[décisions d'architecture](docs/architecture/adr/).

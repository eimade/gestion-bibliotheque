#!/usr/bin/env bash
# Démarre la démonstration complète (GitHub Codespaces ou poste local avec Docker) :
# secrets aléatoires propres à cette copie, construction et lancement de la pile, jeu de données de démonstration.
# Usage : bash demo/start-demo.sh            (DEMO_PORT=8090 bash demo/start-demo.sh pour changer de port)
#         bash demo/start-demo.sh --reset    (repart d'une base vierge avec le jeu de données initial)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/demo/.env.demo"
PROJECT="${DEMO_PROJECT:-bibliotheque-demo}"
PORT="${DEMO_PORT:-8080}"
compose() { docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$ROOT/docker-compose.yml" "$@"; }

if [[ "${1:-}" == "--reset" && -f "$ENV_FILE" ]]; then
  echo "→ Réinitialisation de la démonstration"
  compose down -v
  rm -f "$ROOT/demo/.seeded"
fi

if [[ ! -f "$ENV_FILE" ]]; then
  secret() { tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24; }
  cat > "$ENV_FILE" <<EOF
APP_VERSION=demo
FRONTEND_PORT=$PORT
DB_NAME=bibliotheque
MARIADB_ROOT_PASSWORD=$(secret)
DB_MIGRATION_USER=bibliotheque_migration
DB_MIGRATION_PASSWORD=$(secret)
DB_APP_USER=bibliotheque_app
DB_APP_PASSWORD=$(secret)
APP_BOOTSTRAP_ADMIN_EMAIL=admin@bibliotheque.example
APP_BOOTSTRAP_ADMIN_PASSWORD=Aa1$(secret)
BACKUP_RETENTION_DAYS=1
BACKUP_INTERVAL_SECONDS=86400
EOF
fi

echo "→ Construction et démarrage de la pile (la première fois : 5 à 10 minutes)"
compose up -d --build

echo "→ Attente du backend"
backend="$(compose ps -q backend)"
for _ in $(seq 1 120); do
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "$backend")" == "healthy" ]] && break
  sleep 5
done
[[ "$(docker inspect -f '{{.State.Health.Status}}' "$backend")" == "healthy" ]] || { echo "Le backend n'a pas démarré."; compose logs --tail 80 backend; exit 1; }

if [[ ! -f "$ROOT/demo/.seeded" ]]; then
  echo "→ Jeu de données de démonstration"
  admin_password="$(grep '^APP_BOOTSTRAP_ADMIN_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
  node "$ROOT/demo/seed.mjs" "http://localhost:$PORT" "$admin_password" "$(compose ps -q db)"
  touch "$ROOT/demo/.seeded"
fi

cat <<EOF

  ✅ Démonstration prête sur le port $PORT (onglet « Ports » dans Codespaces).

  Comptes de démonstration
    Administrateur  : admin@bibliotheque.example      / DemoAdmin2026x
    Bibliothécaire  : nadia.rami@bibliotheque.example / NadiaGuichet2026
    Adhérent        : jean.martin@exemple.fr          / LecteurDemo2026

  Repartir des données initiales : bash demo/start-demo.sh --reset
EOF

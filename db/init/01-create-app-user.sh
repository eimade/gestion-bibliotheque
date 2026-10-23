#!/bin/bash
# Exécuté une seule fois par l'image MariaDB, à l'initialisation d'un volume vide (ADR-005).
# Crée l'utilisateur applicatif avec les seuls droits de manipulation de données :
# le schéma reste la propriété de l'utilisateur de migration (Flyway).
# Ce script peut être exécuté ou « sourcé » par l'entrypoint : pas de `set -e` ni de `exit`.

if [ -z "${DB_APP_USER:-}" ] || [ -z "${DB_APP_PASSWORD:-}" ] || [ -z "${MARIADB_DATABASE:-}" ]; then
    echo "01-create-app-user.sh : DB_APP_USER, DB_APP_PASSWORD et MARIADB_DATABASE sont obligatoires" >&2
    return 1 2>/dev/null || exit 1
fi

# Échappement des apostrophes pour les littéraux SQL
app_password_sql="${DB_APP_PASSWORD//\'/\'\'}"

mariadb --protocol=socket -uroot -p"${MARIADB_ROOT_PASSWORD}" <<EOSQL
CREATE USER IF NOT EXISTS '${DB_APP_USER}'@'%' IDENTIFIED BY '${app_password_sql}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MARIADB_DATABASE}\`.* TO '${DB_APP_USER}'@'%';
FLUSH PRIVILEGES;
EOSQL

echo "01-create-app-user.sh : utilisateur applicatif '${DB_APP_USER}' créé"

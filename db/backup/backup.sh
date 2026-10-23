#!/bin/bash
# Sauvegarde logique périodique de la base (NFR-12). Le mot de passe est transmis par MYSQL_PWD
# (jamais en argument de ligne de commande). Les fichiers sont écrits dans /backups puis purgés
# au-delà de BACKUP_RETENTION_DAYS jours. Copier régulièrement /backups hors de l'hôte.
set -uo pipefail

: "${DB_HOST:?}" "${DB_NAME:?}" "${DB_BACKUP_USER:?}" "${MYSQL_PWD:?}"
retention_days="${BACKUP_RETENTION_DAYS:-14}"
interval_seconds="${BACKUP_INTERVAL_SECONDS:-86400}"

while true; do
    timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
    target="/backups/${DB_NAME}-${timestamp}.sql.gz"

    if mariadb-dump --host="${DB_HOST}" --user="${DB_BACKUP_USER}" \
            --single-transaction --quick --routines --triggers "${DB_NAME}" \
        | gzip > "${target}.part"; then
        mv "${target}.part" "${target}"
        echo "$(date -u +%FT%TZ) sauvegarde réussie : ${target}"
    else
        rm -f "${target}.part"
        echo "$(date -u +%FT%TZ) ÉCHEC de la sauvegarde de ${DB_NAME}" >&2
    fi

    find /backups -name "${DB_NAME}-*.sql.gz" -type f -mtime +"${retention_days}" -delete
    sleep "${interval_seconds}"
done

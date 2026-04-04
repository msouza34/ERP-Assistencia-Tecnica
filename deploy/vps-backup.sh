#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

if [ ! -f .env ]; then
  echo "Erro: arquivo .env nao encontrado em $REPO_DIR"
  exit 1
fi

ID_ONLY=false
if [ "${1:-}" = "--id-only" ]; then
  ID_ONLY=true
fi

BACKUP_ROOT="${REPO_DIR}/.deploy-backups"
mkdir -p "$BACKUP_ROOT"

BACKUP_ID="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="${BACKUP_ROOT}/${BACKUP_ID}"
mkdir -p "$BACKUP_DIR"

git rev-parse HEAD > "${BACKUP_DIR}/git-commit.txt"
git rev-parse --abbrev-ref HEAD > "${BACKUP_DIR}/git-branch.txt" || true
cp docker-compose.yml "${BACKUP_DIR}/docker-compose.yml"
docker compose --env-file .env ps --format json > "${BACKUP_DIR}/compose-ps.json" || true
docker compose --env-file .env images > "${BACKUP_DIR}/compose-images.txt" || true

# Best effort database dump; keeps backup local to the VPS only.
if docker compose --env-file .env exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > "${BACKUP_DIR}/postgres.sql" 2>/dev/null; then
  :
else
  rm -f "${BACKUP_DIR}/postgres.sql"
fi

if [ "$ID_ONLY" = true ]; then
  echo "$BACKUP_ID"
  exit 0
fi

echo "Backup criado: ${BACKUP_ID}"
echo "Diretorio: ${BACKUP_DIR}"

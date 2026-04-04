#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

if [ ! -f .env ]; then
  echo "Erro: arquivo .env nao encontrado em $REPO_DIR"
  exit 1
fi

BACKUP_ROOT="${REPO_DIR}/.deploy-backups"
if [ ! -d "$BACKUP_ROOT" ]; then
  echo "Erro: nenhum backup encontrado em $BACKUP_ROOT"
  exit 1
fi

TARGET="${1:-latest}"
WITH_DB_RESTORE="${2:-}"

if [ "$TARGET" = "latest" ]; then
  TARGET_DIR="$(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
else
  TARGET_DIR="${BACKUP_ROOT}/${TARGET}"
fi

if [ -z "${TARGET_DIR:-}" ] || [ ! -d "$TARGET_DIR" ]; then
  echo "Erro: backup alvo nao encontrado."
  exit 1
fi

if [ ! -f "${TARGET_DIR}/git-commit.txt" ]; then
  echo "Erro: backup invalido. git-commit.txt ausente em ${TARGET_DIR}"
  exit 1
fi

TARGET_COMMIT="$(cat "${TARGET_DIR}/git-commit.txt")"
echo "Rollback para commit: ${TARGET_COMMIT}"
echo "Backup usado: $(basename "$TARGET_DIR")"

git fetch --all --tags
git checkout -f "$TARGET_COMMIT"

docker compose --env-file .env build --pull
docker compose --env-file .env up -d --remove-orphans

if [ "$WITH_DB_RESTORE" = "--with-db" ]; then
  if [ -f "${TARGET_DIR}/postgres.sql" ]; then
    cat "${TARGET_DIR}/postgres.sql" | docker compose --env-file .env exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
    echo "Restauracao de banco concluida."
  else
    echo "Aviso: postgres.sql nao encontrado no backup. Restauracao ignorada."
  fi
fi

echo "Rollback concluido."

#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

if [ ! -f .env ]; then
  echo "Erro: arquivo .env nao encontrado em $REPO_DIR"
  exit 1
fi

if [ ! -d deploy/migrations ]; then
  echo "Nenhum diretorio de migracoes encontrado."
  exit 0
fi

shopt -s nullglob
files=(deploy/migrations/*.sql)

if [ ${#files[@]} -eq 0 ]; then
  echo "Nenhuma migracao SQL pendente."
  exit 0
fi

for file in "${files[@]}"; do
  echo "Aplicando migracao: $(basename "$file")"
  docker compose --env-file .env exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$file"
done

echo "Migracoes finalizadas."

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$REPO_DIR"

if [ ! -f .env ]; then
  echo "Erro: arquivo .env nao encontrado em $REPO_DIR"
  exit 1
fi

TARGET_REF="${1:-origin/main}"
BACKUP_ID="$("${SCRIPT_DIR}/vps-backup.sh" --id-only)"
echo "Backup de seguranca criado: ${BACKUP_ID}"

rollback_on_error() {
  echo "Falha durante update. Iniciando rollback automatico..."
  "${SCRIPT_DIR}/vps-rollback.sh" "${BACKUP_ID}" || true
}

trap rollback_on_error ERR

git fetch --all --tags
git checkout -B main "${TARGET_REF}"

"${SCRIPT_DIR}/vps-migrate.sh"
"${SCRIPT_DIR}/vps-build.sh"
"${SCRIPT_DIR}/vps-up.sh"

trap - ERR

echo "Deploy concluido com sucesso."
echo "Backup disponivel em .deploy-backups/${BACKUP_ID}"


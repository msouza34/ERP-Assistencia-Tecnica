#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

if [ ! -f .env ]; then
  echo "Erro: arquivo .env nao encontrado em $REPO_DIR"
  exit 1
fi

docker compose --env-file .env build --pull

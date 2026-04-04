# Sistema Danicell - ERP de Assistencia Tecnica

Sistema para assistencia tecnica com foco em ordens de servico, estoque, financeiro, clientes, auditoria e dashboard operacional.

## Descricao do projeto

O projeto e composto por:
- Backend Spring Boot com autenticacao JWT, multi-tenant e controle de acesso por perfis.
- Frontend React + Vite para operacao diaria da assistencia tecnica.
- PostgreSQL para persistencia.
- Redis para controle de tentativa de login e suporte a recursos de seguranca.
- Caddy como reverse proxy com HTTPS.
- Docker Compose para orquestracao e deploy em VPS.

## Tecnologias usadas

- Java 17
- Spring Boot 3
- Spring Security, Spring Data JPA, Actuator
- PostgreSQL 16
- Redis 7
- React 18 + Vite 5
- Docker + Docker Compose
- Caddy 2

## Estrutura do projeto

```text
.
|-- backend/
|   |-- src/
|   |-- Dockerfile
|   `-- .dockerignore
|-- frontend/
|   |-- src/
|   |-- Dockerfile
|   `-- .dockerignore
|-- caddy/
|   `-- Caddyfile
|-- deploy/
|   |-- vps-build.sh
|   |-- vps-up.sh
|   `-- vps-restart.sh
|-- docker-compose.yml
|-- .env.example
|-- .gitignore
`-- README.md
```

## Como rodar localmente

1. Copie o arquivo de exemplo:

```bash
cp .env.example .env
```

2. Ajuste variaveis no `.env` para ambiente local (exemplo recomendado):

```dotenv
APP_DOMAIN=localhost
API_DOMAIN=localhost
APP_BASE_URL=http://localhost
VITE_API_URL=/
CORS_ALLOWED_ORIGINS=http://localhost
HTTP_PORT=80
HTTPS_PORT=443
```

3. Defina segredos fortes no `.env`:
- `JWT_SECRET` com 32+ caracteres.
- `POSTGRES_PASSWORD` e `REDIS_PASSWORD` fortes.
- `AUTH_PASSWORD_HASH` em bcrypt (nunca senha em texto puro).

4. Suba os servicos:

```bash
docker compose --env-file .env build --pull
docker compose --env-file .env up -d
```

5. Verifique status:

```bash
docker compose ps
docker compose logs -f backend
```

## Como fazer deploy na VPS

1. Preparar servidor:
- Instalar Docker e Docker Compose plugin.
- Liberar portas `80` e `443` no firewall da VPS.
- Criar DNS tipo `A` para `APP_DOMAIN` e `API_DOMAIN` apontando para a VPS.

2. Clonar projeto e configurar ambiente:

```bash
git clone <URL_DO_REPOSITORIO>
cd ERP-Assistencia-Tecnica
cp .env.example .env
```

3. Editar `.env` com valores de producao (sem placeholders):
- Dominio real (`APP_DOMAIN`, `API_DOMAIN`).
- Email valido para certificado TLS (`TLS_EMAIL`).
- Segredos e senhas fortes.
- `JPA_DDL_AUTO=validate` em producao.

4. Subir stack:

```bash
chmod +x deploy/*.sh
./deploy/vps-build.sh
./deploy/vps-up.sh
```

5. Reiniciar servicos sem rebuild:

```bash
./deploy/vps-restart.sh
```

## Variaveis de ambiente

Base oficial: `.env.example`

### Rede e dominio
- `APP_DOMAIN`: dominio do frontend (ex: `app.example.com`)
- `API_DOMAIN`: dominio da API (ex: `api.example.com`)
- `TLS_EMAIL`: email usado pelo Caddy para HTTPS
- `APP_BASE_URL`: URL publica do frontend
- `VITE_API_URL`: URL publica da API (ou `/` quando servido pelo mesmo host)
- `CORS_ALLOWED_ORIGINS`: origens permitidas para CORS

### Backend
- `SERVER_PORT`: porta interna da API no container
- `JWT_SECRET`: segredo JWT (obrigatorio, minimo 32 caracteres)
- `JWT_EXPIRATION_MINUTES`: expiracao do token
- `TENANT_DEFAULT_ID`: tenant padrao
- `JPA_DDL_AUTO`: recomendado `validate` em producao

### Banco de dados
- `POSTGRES_DB`: nome do banco
- `POSTGRES_USER`: usuario do banco
- `POSTGRES_PASSWORD`: senha do banco
- `POSTGRES_PORT`: porta interna do postgres na rede Docker

### Redis
- `REDIS_HOST`: host do redis (padrao `redis`)
- `REDIS_PORT`: porta do redis
- `REDIS_PASSWORD`: senha do redis (obrigatoria)

### Bootstrap de autenticacao
- `AUTH_USERNAME`: usuario inicial (opcional)
- `AUTH_PASSWORD_HASH`: hash bcrypt do usuario inicial (opcional)
- `AUTH_ROLE`: `ADMIN`, `ATENDENTE` ou `TECNICO`
- `AUTH_LEGACY_ENABLED`: fallback legado por env (`false` recomendado)

### Anti brute force
- `LOGIN_MAX_ATTEMPTS`
- `LOGIN_LOCK_MINUTES`
- `LOGIN_WINDOW_MINUTES`

## Comandos Docker

Build da stack:

```bash
docker compose --env-file .env build --pull
```

Subir stack:

```bash
docker compose --env-file .env up -d
```

Reiniciar servicos principais:

```bash
docker compose --env-file .env restart backend frontend proxy
```

Parar stack:

```bash
docker compose down
```

Logs:

```bash
docker compose logs -f backend
docker compose logs -f proxy
```

## Backup e rollback na VPS

Scripts adicionados para operacao segura em producao:

- `deploy/vps-backup.sh`: cria snapshot local de rollback em `.deploy-backups/`.
- `deploy/vps-rollback.sh`: volta para um snapshot anterior.
- `deploy/vps-safe-update.sh`: faz backup + update com rollback automatico em caso de falha.

Comando unico recomendado para atualizar em producao:

```bash
./deploy/vps-safe-update.sh
```

Comandos manuais:

```bash
./deploy/vps-backup.sh
./deploy/vps-rollback.sh latest
./deploy/vps-rollback.sh <BACKUP_ID> --with-db
```

## Boas praticas de seguranca

- Nunca versionar `.env`.
- Nunca commitar senhas, chaves, tokens ou dumps.
- Usar segredos fortes e unicos por ambiente (dev/hml/prod).
- Rotacionar segredos periodicamente e apos qualquer suspeita de vazamento.
- Manter banco e redis sem publicacao de porta externa.
- Expor somente `80/443` via proxy reverso.
- Manter `JPA_DDL_AUTO=validate` em producao.
- Monitorar logs e endpoint de saude (`/actuator/health`).
- Atualizar imagens Docker e dependencias com frequencia.

## Versionamento seguro no GitHub

Antes do primeiro push:

```bash
git init
git add .
git status
```

Checklist rapido:
- O arquivo `.env` e ignorado automaticamente por `.gitignore`.
- `.env` nao aparece como staged.
- `frontend/node_modules`, `frontend/dist` e `backend/target` nao aparecem como staged.
- Nenhuma credencial real em arquivos versionados.

Push inicial (exemplo):

```bash
git branch -M main
git remote add origin <URL_DO_REPOSITORIO>
git commit -m "chore: prepare production-ready secure stack"
git push -u origin main
```

## Validacao final para producao

Este repositorio foi preparado para:
- Evitar versionamento de dados sensiveis (`.gitignore` reforcado).
- Rodar com variaveis de ambiente para dados sensiveis.
- Isolar rede interna de banco e redis no Docker.
- Executar deploy em VPS com scripts dedicados.
- Garantir documentacao operacional completa.

Se houver historico antigo com segredos, faca rotacao imediata de todas as credenciais e limpe o historico Git antes de publicar.

#!/usr/bin/env bash
# Despliegue de Alcanfores en el VPS (lo ejecuta GitHub Actions por SSH, o a mano):
#   cd /opt/alcanfores && ./deploy/desplegar.sh
# 1) trae la última versión de main, 2) construye las imágenes etiquetadas con el commit,
# 3) levanta los contenedores, 4) espera a que TODOS estén sanos y el gateway responda,
# 5) si algo falla vuelve a la versión anterior (rollback) y termina con error. El rollback vuelve a levantar TODOS los
#    servicios de esa versión, Canchita (asistente-service) incluida; si la versión anterior no la tenía, la quita
#    (--remove-orphans) y su base queda intacta para el próximo intento.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
COMPOSE=(docker compose -f deploy/docker-compose.yml --env-file deploy/.env)
ESPERA_MAX=${ESPERA_MAX:-420}

registro() { printf '\033[1;32m[%s]\033[0m %s\n' "$(date '+%H:%M:%S')" "$*"; }
error() { printf '\033[1;31m[%s] %s\033[0m\n' "$(date '+%H:%M:%S')" "$*" >&2; }

[ -f deploy/.env ] || { error "Falta deploy/.env (copiar de deploy/.env.example)"; exit 1; }

ANTERIOR="$(cat .version 2>/dev/null || echo ninguna)"
registro "Versión en producción: $ANTERIOR"

registro "Actualizando código desde GitHub (main)"
git fetch --quiet origin main
git reset --quiet --hard origin/main
NUEVA="$(git rev-parse --short HEAD)"
registro "Versión a desplegar: $NUEVA ($(git log -1 --pretty=%s))"

export VERSION="$NUEVA"
registro "Construyendo imágenes (7 microservicios + Canchita + web)"
DOCKER_BUILDKIT=1 "${COMPOSE[@]}" build --pull

# Base y usuario de Canchita (asistente-service). Los scripts de deploy/mysql solo corren al crear el volumen de MySQL;
# en el servidor MySQL ya existe, así que aquí se crean si faltan (idempotente). La clave sale de deploy/.env; si no
# está, se genera una y se agrega al .env (con respaldo).
asegurar_bd_asistente() {
  local clave existe
  # «|| true»: con set -euo pipefail, un grep sin coincidencias (primer despliegue, clave aún no creada) cortaba el script.
  clave="$( { grep -E '^ASISTENTE_DB_CLAVE=' deploy/.env || true; } | tail -1 | cut -d= -f2- | tr -d "\r\"'")"
  if [ -z "$clave" ] || [ "$clave" = "cambiar" ]; then
    clave="$(openssl rand -hex 24)"
    cp deploy/.env "deploy/.env.antes-asistente-$(date +%Y%m%d%H%M%S)"
    sed -i '/^ASISTENTE_DB_CLAVE=/d' deploy/.env
    printf '\nASISTENTE_DB_CLAVE=%s\n' "$clave" >> deploy/.env
    registro "ASISTENTE_DB_CLAVE generada y agregada a deploy/.env"
  fi
  [[ "$clave" =~ ^[A-Za-z0-9._@%+=:,/-]+$ ]] || { error "ASISTENTE_DB_CLAVE solo admite letras, números y . _ @ % + = : , / -"; return 1; }
  "${COMPOSE[@]}" up -d mysql
  local inicio=$SECONDS
  until "${COMPOSE[@]}" exec -T mysql sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' >/dev/null 2>&1; do
    (( SECONDS - inicio < 120 )) || { error "MySQL no responde"; return 1; }
    sleep 3
  done
  # El SQL (con la clave) entra por la entrada estándar: no aparece en la lista de procesos.
  "${COMPOSE[@]}" exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" 2>/dev/null' <<SQL
CREATE DATABASE IF NOT EXISTS alcanfores_asistente CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'asistente_svc'@'%' IDENTIFIED BY '${clave}';
ALTER USER 'asistente_svc'@'%' IDENTIFIED BY '${clave}';
GRANT ALL PRIVILEGES ON alcanfores_asistente.* TO 'asistente_svc'@'%';
FLUSH PRIVILEGES;
SQL
  existe="$("${COMPOSE[@]}" exec -T mysql sh -c "mysql -N -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -e \"SELECT COUNT(*) FROM mysql.user WHERE user = 'asistente_svc'\" 2>/dev/null" | tr -d '\r[:space:]')"
  [ "$existe" = "1" ] || { error "No se pudo crear el usuario asistente_svc en MySQL"; return 1; }
  registro "Base alcanfores_asistente y usuario asistente_svc listos"
}
asegurar_bd_asistente

registro "Levantando contenedores"
"${COMPOSE[@]}" up -d --remove-orphans

esperar_salud() {
  local inicio=$SECONDS
  while (( SECONDS - inicio < ESPERA_MAX )); do
    local pendientes
    pendientes=$("${COMPOSE[@]}" ps --format '{{.Service}} {{.Health}}' | awk '$2!="healthy"{print $1}' | tr '\n' ' ')
    if [ -z "$pendientes" ]; then
      return 0
    fi
    sleep 10
    registro "Esperando servicios sanos: $pendientes"
  done
  return 1
}

if esperar_salud && "${COMPOSE[@]}" exec -T alcanfores-web wget -qO- http://api-gateway:8086/actuator/health | grep -q '"UP"'; then
  echo "$NUEVA" > .version
  registro "Despliegue OK: versión $NUEVA en producción"
  "${COMPOSE[@]}" exec -T alcanfores-web wget -qO- http://api-gateway:8086/api/estado || true
  echo
  docker image prune -f --filter "until=72h" >/dev/null || true
  exit 0
fi

error "La versión $NUEVA no quedó sana."
"${COMPOSE[@]}" ps
if [ "$ANTERIOR" != "ninguna" ]; then
  error "Volviendo a la versión $ANTERIOR (rollback)"
  git reset --quiet --hard "$ANTERIOR"
  VERSION="$ANTERIOR" "${COMPOSE[@]}" up -d --remove-orphans
fi
exit 1

#!/usr/bin/env bash
# Despliegue de Alcanfores en el VPS (lo ejecuta GitHub Actions por SSH, o a mano):
#   cd /opt/alcanfores && ./deploy/desplegar.sh
# 1) trae la última versión de main, 2) construye las imágenes etiquetadas con el commit,
# 3) levanta los contenedores, 4) espera a que TODOS estén sanos y el gateway responda,
# 5) si algo falla vuelve a la versión anterior (rollback) y termina con error.
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
registro "Construyendo imágenes (7 microservicios + web)"
DOCKER_BUILDKIT=1 "${COMPOSE[@]}" build --pull

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

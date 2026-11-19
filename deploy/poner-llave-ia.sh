#!/usr/bin/env bash
# Carga la credencial de Canchita (asistente con IA) en el .env del servidor SIN mostrarla.
#   Token de la suscripción (claude setup-token → sk-ant-oat01-…)  → CLAUDE_CODE_OAUTH_TOKEN
#   Llave de la API de Anthropic (sk-ant-api…)                       → ANTHROPIC_API_KEY
# La credencial entra por la entrada estándar (nunca como argumento: no queda en el historial ni en la lista de
# procesos). Desde Windows lo usa deploy/conectar-canchita.ps1:   $token | ssh … 'bash ~/poner-llave-ia.sh'
# A mano en el servidor:   bash deploy/poner-llave-ia.sh      (pega el token y presiona Enter; no se ve en pantalla)
# Hace respaldo del .env (.env.antes-token), reemplaza la línea y, si asistente-service existe, lo recrea para que
# tome la credencial nueva. No toca ningún otro servicio.
set -euo pipefail

DIR="${ALCANFORES_DIR:-/opt/alcanfores}"
ENV_FILE="$DIR/deploy/.env"
[ -f "$ENV_FILE" ] || { echo "No encuentro $ENV_FILE (usa ALCANFORES_DIR=/ruta/del/repo)." >&2; exit 1; }

if [ -t 0 ]; then
  printf 'Pega el token de Claude (no se mostrará) y presiona Enter: ' >&2
  IFS= read -rs token
  echo >&2
else
  IFS= read -r token || true
fi
token="$(printf '%s' "$token" | tr -d '[:space:]')"
token="${token#CLAUDE_CODE_OAUTH_TOKEN=}"
token="${token#ANTHROPIC_API_KEY=}"

case "$token" in
  sk-ant-oat*) variable=CLAUDE_CODE_OAUTH_TOKEN; otra=ANTHROPIC_API_KEY ;;
  sk-ant-api*) variable=ANTHROPIC_API_KEY; otra=CLAUDE_CODE_OAUTH_TOKEN ;;
  *) echo "Eso no parece una credencial de Claude (debe empezar con sk-ant-oat o sk-ant-api)." >&2; exit 1 ;;
esac
if ! [[ "$token" =~ ^sk-ant-[A-Za-z0-9_-]{40,}$ ]]; then
  echo "La credencial está incompleta o tiene caracteres extraños. Cópiala de nuevo." >&2
  exit 1
fi

cp -p "$ENV_FILE" "$ENV_FILE.antes-token"
chmod 600 "$ENV_FILE.antes-token"
temporal="$(mktemp "$DIR/deploy/.env.XXXXXX")"
# Se reemplaza la línea de la variable; la otra credencial se vacía para que el SDK use solo la nueva.
grep -v -E "^(${variable}|${otra})=" "$ENV_FILE" > "$temporal" || true
printf '%s=%s\n%s=\n' "$variable" "$token" "$otra" >> "$temporal"
chmod --reference="$ENV_FILE" "$temporal" 2>/dev/null || chmod 600 "$temporal"
mv "$temporal" "$ENV_FILE"
unset token
echo "Credencial guardada en $ENV_FILE ($variable; respaldo en .env.antes-token)."

COMPOSE=(docker compose -f "$DIR/deploy/docker-compose.yml" --env-file "$ENV_FILE")
if [ -n "$(cd "$DIR" && "${COMPOSE[@]}" ps -a -q asistente-service 2>/dev/null)" ]; then
  echo "Reiniciando Canchita (asistente-service) con la credencial nueva…"
  (cd "$DIR" && VERSION="$(cat .version 2>/dev/null || echo local)" "${COMPOSE[@]}" up -d --no-deps --force-recreate asistente-service)
  echo "Listo: Canchita ya usa la credencial nueva."
else
  echo "asistente-service aún no está desplegado: tomará la credencial en el próximo despliegue."
fi

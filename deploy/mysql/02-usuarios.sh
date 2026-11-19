#!/bin/bash
# Crea un usuario MySQL por microservicio con permisos solo sobre su base (se ejecuta una vez, al crear el volumen).
set -euo pipefail
for svc in auth catalogo reservas pagos notificaciones; do
  var="DB_CLAVE_${svc^^}"
  clave="${!var}"
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${svc}_svc'@'%' IDENTIFIED BY '${clave}';
GRANT ALL PRIVILEGES ON alcanfores_${svc}.* TO '${svc}_svc'@'%';
SQL
done
# Canchita (asistente-service): su usuario lo crea SIEMPRE deploy/desplegar.sh (idempotente), también en un MySQL que
# ya existía. Aquí solo si la variable llega al contenedor (no se agrega al compose para no recrear MySQL en producción).
if [ -n "${ASISTENTE_DB_CLAVE:-}" ]; then
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'asistente_svc'@'%' IDENTIFIED BY '${ASISTENTE_DB_CLAVE}';
GRANT ALL PRIVILEGES ON alcanfores_asistente.* TO 'asistente_svc'@'%';
SQL
fi
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"
echo "Usuarios de los microservicios creados."

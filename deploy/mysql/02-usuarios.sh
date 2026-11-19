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
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"
echo "Usuarios de los microservicios creados."

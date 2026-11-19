-- Una base de datos por microservicio (patrón database-per-service) en el mismo servidor MySQL 8.4.
-- Cada servicio tiene su propio usuario con permisos SOLO sobre su base: ninguno puede leer las tablas de otro.
-- Las claves llegan por variables de entorno a 02-usuarios.sh.
CREATE DATABASE IF NOT EXISTS alcanfores_auth           CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS alcanfores_catalogo       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS alcanfores_reservas       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS alcanfores_pagos          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS alcanfores_notificaciones CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

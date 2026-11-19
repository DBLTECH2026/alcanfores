// Migraciones SQL simples (válidas en MariaDB 10.4 y MySQL 8.4). Se aplican al arrancar, en orden, una sola vez.

export const MIGRACIONES: { version: number; descripcion: string; sql: string[] }[] = [
  {
    version: 1,
    descripcion: "Conversaciones, mensajes e imágenes de Canchita",
    sql: [
      `CREATE TABLE IF NOT EXISTS conversacion (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        canal ENUM('WHATSAPP','WEB') NOT NULL,
        clave VARCHAR(80) NOT NULL,
        nombre VARCHAR(120) NULL,
        estado ENUM('ACTIVA','PAUSADA') NOT NULL DEFAULT 'ACTIVA',
        pausada_hasta DATETIME NULL,
        codigo_reserva VARCHAR(12) NULL,
        dni CHAR(8) NULL,
        contexto JSON NULL,
        creada_en DATETIME NOT NULL,
        ultima_actividad DATETIME NOT NULL,
        UNIQUE KEY uq_conversacion_canal_clave (canal, clave),
        KEY ix_conversacion_actividad (ultima_actividad)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
      `CREATE TABLE IF NOT EXISTS mensaje (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        id_conversacion BIGINT NOT NULL,
        rol ENUM('CLIENTE','CANCHITA','EQUIPO') NOT NULL,
        texto TEXT NOT NULL,
        adjunto VARCHAR(255) NULL,
        id_externo VARCHAR(80) NULL,
        latencia_ms INT NULL,
        extras JSON NULL,
        atendido TINYINT(1) NOT NULL DEFAULT 0,
        creado_en DATETIME(3) NOT NULL,
        UNIQUE KEY uq_mensaje_externo (id_externo),
        KEY ix_mensaje_conversacion (id_conversacion, id),
        CONSTRAINT fk_mensaje_conversacion FOREIGN KEY (id_conversacion) REFERENCES conversacion (id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
      `CREATE TABLE IF NOT EXISTS imagen (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        id_conversacion BIGINT NOT NULL,
        mime VARCHAR(40) NOT NULL,
        datos MEDIUMBLOB NOT NULL,
        lectura JSON NULL,
        id_pago BIGINT NULL,
        validacion TEXT NULL,
        creado_en DATETIME NOT NULL,
        KEY ix_imagen_pago (id_pago),
        CONSTRAINT fk_imagen_conversacion FOREIGN KEY (id_conversacion) REFERENCES conversacion (id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
    ],
  },
];

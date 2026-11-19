-- notificaciones-service · v2 (válido en MariaDB 10.4 y MySQL 8.4)
-- 1) Correo configurable desde el panel (una sola fila, id = 1). La clave va cifrada con AES-GCM.
-- 2) Varios recordatorios por reserva: una fila por (reserva, minutos antes).

CREATE TABLE config_correo (
    id               TINYINT      NOT NULL,
    host             VARCHAR(120) NOT NULL,
    puerto           INT          NOT NULL,
    usuario          VARCHAR(100) NOT NULL,
    clave_cifrada    VARCHAR(600) NULL COMMENT 'AES-GCM, base64(iv + cifrado); nunca se devuelve',
    remitente        VARCHAR(100) NOT NULL,
    nombre_remitente VARCHAR(80)  NOT NULL,
    seguridad        ENUM('SSL','STARTTLS') NOT NULL,
    actualizado_en   DATETIME     NOT NULL,
    actualizado_por  VARCHAR(60)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_config_correo_unica CHECK (id = 1),
    CONSTRAINT ck_config_correo_puerto CHECK (puerto BETWEEN 1 AND 65535)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE recordatorio ADD COLUMN minutos_antes INT NOT NULL DEFAULT 120 AFTER codigo_reserva;
UPDATE recordatorio SET minutos_antes = GREATEST(TIMESTAMPDIFF(MINUTE, enviar_en, inicio_reserva), 1);
ALTER TABLE recordatorio DROP INDEX uq_recordatorio_reserva;
ALTER TABLE recordatorio ADD CONSTRAINT uq_recordatorio_reserva_minutos UNIQUE (codigo_reserva, minutos_antes);

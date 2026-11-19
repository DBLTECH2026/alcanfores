-- notificaciones-service · BD alcanfores_notificaciones (SPEC §5.5)
-- SQL válido en MariaDB 10.4 (local) y MySQL 8.4 (producción).

CREATE TABLE notificacion (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    codigo_reserva VARCHAR(12)  NULL,
    evento         VARCHAR(40)  NOT NULL COMMENT 'routing key del evento, recordatorio o whatsapp.prueba',
    canal          ENUM('WHATSAPP','CORREO') NOT NULL,
    destino        VARCHAR(120) NOT NULL COMMENT 'correo o número 51XXXXXXXXX',
    asunto         VARCHAR(200) NOT NULL,
    cuerpo         MEDIUMTEXT   NOT NULL COMMENT 'HTML del correo o texto del WhatsApp',
    adjuntos       JSON         NULL COMMENT '[{tipo, referencia, nombreArchivo, estado, detalle}]',
    estado         ENUM('PENDIENTE','ENVIADO','FALLIDO','SIMULADO') NOT NULL DEFAULT 'PENDIENTE',
    intentos       INT          NOT NULL DEFAULT 0,
    ultimo_error   VARCHAR(500) NULL,
    creado_en      DATETIME     NOT NULL,
    enviado_en     DATETIME     NULL,
    PRIMARY KEY (id),
    KEY ix_notificacion_reserva (codigo_reserva),
    KEY ix_notificacion_estado (estado, intentos),
    KEY ix_notificacion_creado (creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE recordatorio (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    codigo_reserva VARCHAR(12)  NOT NULL,
    enviar_en      DATETIME     NOT NULL COMMENT 'inicio de la reserva menos recordatorio_horas',
    inicio_reserva DATETIME     NOT NULL,
    estado         ENUM('PROGRAMADO','ENVIADO','CANCELADO','OMITIDO') NOT NULL DEFAULT 'PROGRAMADO',
    datos          JSON         NOT NULL COMMENT '{reserva: DatosReserva, saldoPendiente}',
    enviado_en     DATETIME     NULL,
    detalle        VARCHAR(255) NULL COMMENT 'motivo de cancelación u omisión',
    PRIMARY KEY (id),
    CONSTRAINT uq_recordatorio_reserva UNIQUE (codigo_reserva),
    KEY ix_recordatorio_pendientes (estado, enviar_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE evento_procesado (
    id_evento    CHAR(36)    NOT NULL,
    tipo         VARCHAR(40) NOT NULL,
    procesado_en DATETIME    NOT NULL,
    PRIMARY KEY (id_evento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

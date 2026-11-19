-- reservas-service · esquema inicial (válido en MySQL 8.4 y MariaDB 10.4)
-- Respeta el diccionario de datos del informe (cliente, turno, reserva, cancelacion) y lo amplía.

CREATE TABLE cliente (
    id_cliente        BIGINT       NOT NULL AUTO_INCREMENT,
    dni               CHAR(8)      NOT NULL,
    nombres           VARCHAR(60)  NOT NULL,
    apellidos         VARCHAR(60)  NOT NULL,
    telefono          CHAR(9)      NOT NULL,
    correo            VARCHAR(100) NOT NULL,
    verificado_reniec BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_registro    DATETIME     NOT NULL,
    actualizado_en    DATETIME     NOT NULL,
    PRIMARY KEY (id_cliente),
    CONSTRAINT uq_cliente_dni UNIQUE (dni)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE turno (
    id_turno       BIGINT        NOT NULL AUTO_INCREMENT,
    id_cancha      BIGINT        NOT NULL,
    nombre_cancha  VARCHAR(60)   NOT NULL,
    deporte        VARCHAR(20)   NOT NULL,
    fecha          DATE          NOT NULL,
    hora_inicio    TIME          NOT NULL,
    hora_fin       TIME          NOT NULL,
    precio         DECIMAL(10,2) NOT NULL,
    franja         ENUM('DIA','NOCHE') NOT NULL,
    estado         ENUM('DISPONIBLE','RESERVADO','BLOQUEADO') NOT NULL DEFAULT 'DISPONIBLE',
    motivo_bloqueo VARCHAR(120)  NULL,
    PRIMARY KEY (id_turno),
    CONSTRAINT uq_turno_cancha_fecha_hora UNIQUE (id_cancha, fecha, hora_inicio),
    INDEX ix_turno_fecha_estado (fecha, estado)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reserva (
    id_reserva           BIGINT        NOT NULL AUTO_INCREMENT,
    codigo_reserva       VARCHAR(12)   NOT NULL,
    id_cliente           BIGINT        NOT NULL,
    id_turno             BIGINT        NOT NULL,
    estado               ENUM('PENDIENTE','CONFIRMADA','CANCELADA') NOT NULL,
    estado_pago          ENUM('SIN_PAGO','EN_REVISION','OBSERVADO','ADELANTO_PAGADO','PAGADO_TOTAL') NOT NULL,
    asistencia           ENUM('PENDIENTE','ASISTIO','NO_ASISTIO') NOT NULL DEFAULT 'PENDIENTE',
    origen               ENUM('WEB','PRESENCIAL','TELEFONICA') NOT NULL,
    metodo_pago_previsto VARCHAR(20)   NULL,
    monto_total          DECIMAL(10,2) NOT NULL,
    monto_adelanto       DECIMAL(10,2) NOT NULL,
    monto_pagado         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    vence_en             DATETIME      NULL,
    fecha_registro       DATETIME      NOT NULL,
    confirmada_en        DATETIME      NULL,
    registrado_por       BIGINT        NULL,
    nota                 VARCHAR(255)  NULL,
    es_semilla           BOOLEAN       NOT NULL DEFAULT FALSE,
    version              BIGINT        NOT NULL DEFAULT 0,
    -- Garantía de NO doble reserva (RNF-11): solo una reserva vigente (PENDIENTE o CONFIRMADA) por turno.
    turno_activo         BIGINT GENERATED ALWAYS AS (
                             CASE WHEN estado IN ('PENDIENTE','CONFIRMADA') THEN id_turno ELSE NULL END) STORED,
    PRIMARY KEY (id_reserva),
    CONSTRAINT uq_reserva_codigo UNIQUE (codigo_reserva),
    CONSTRAINT uq_reserva_turno_activo UNIQUE (turno_activo),
    CONSTRAINT fk_reserva_cliente FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente),
    CONSTRAINT fk_reserva_turno FOREIGN KEY (id_turno) REFERENCES turno (id_turno),
    INDEX ix_reserva_estado (estado, estado_pago),
    INDEX ix_reserva_vence (vence_en),
    INDEX ix_reserva_registro (fecha_registro)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cancelacion (
    id_cancelacion    BIGINT        NOT NULL AUTO_INCREMENT,
    id_reserva        BIGINT        NOT NULL,
    id_administrador  BIGINT        NULL,
    cancelado_por     ENUM('CLIENTE','ADMINISTRADOR','SISTEMA') NOT NULL,
    motivo            VARCHAR(255)  NOT NULL,
    devolucion        BOOLEAN       NOT NULL DEFAULT FALSE,
    monto_devolucion  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fecha_cancelacion DATETIME      NOT NULL,
    PRIMARY KEY (id_cancelacion),
    CONSTRAINT uq_cancelacion_reserva UNIQUE (id_reserva),
    CONSTRAINT fk_cancelacion_reserva FOREIGN KEY (id_reserva) REFERENCES reserva (id_reserva)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Línea de tiempo de cada reserva (registrada, comprobante recibido, observado, confirmada, asistencia, cancelada…)
CREATE TABLE reserva_historial (
    id_historial BIGINT       NOT NULL AUTO_INCREMENT,
    id_reserva   BIGINT       NOT NULL,
    evento       VARCHAR(30)  NOT NULL,
    detalle      VARCHAR(255) NULL,
    fecha        DATETIME     NOT NULL,
    PRIMARY KEY (id_historial),
    CONSTRAINT fk_historial_reserva FOREIGN KEY (id_reserva) REFERENCES reserva (id_reserva),
    INDEX ix_historial_reserva (id_reserva, fecha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Caché de consultas a RENIEC/SUNAT (30 días)
CREATE TABLE consulta_documento (
    tipo          ENUM('DNI','RUC') NOT NULL,
    numero        VARCHAR(11)  NOT NULL,
    respuesta     JSON         NOT NULL,
    fuente        VARCHAR(40)  NOT NULL,
    consultado_en DATETIME     NOT NULL,
    PRIMARY KEY (tipo, numero)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Idempotencia de los consumidores de eventos
CREATE TABLE evento_procesado (
    id_evento    CHAR(36)    NOT NULL,
    tipo         VARCHAR(40) NOT NULL,
    procesado_en DATETIME    NOT NULL,
    PRIMARY KEY (id_evento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

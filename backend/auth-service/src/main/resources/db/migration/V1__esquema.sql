-- auth-service · esquema inicial (válido en MariaDB 10.4 y MySQL 8.4)

CREATE TABLE administrador (
    id_administrador  BIGINT       NOT NULL AUTO_INCREMENT,
    nombres           VARCHAR(60)  NOT NULL,
    apellidos         VARCHAR(60)  NOT NULL,
    usuario           VARCHAR(30)  NOT NULL,
    correo            VARCHAR(100) NOT NULL,
    contrasena_hash   VARCHAR(100) NOT NULL,
    rol               ENUM('ADMIN','RECEPCION') NOT NULL,
    estado            ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
    intentos_fallidos INT          NOT NULL DEFAULT 0,
    bloqueado_hasta   DATETIME     NULL,
    ultimo_acceso     DATETIME     NULL,
    creado_en         DATETIME     NOT NULL,
    actualizado_en    DATETIME     NOT NULL,
    PRIMARY KEY (id_administrador),
    CONSTRAINT uq_administrador_usuario UNIQUE (usuario),
    CONSTRAINT uq_administrador_correo UNIQUE (correo),
    CONSTRAINT ck_administrador_intentos CHECK (intentos_fallidos >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE bitacora (
    id_bitacora BIGINT       NOT NULL AUTO_INCREMENT,
    servicio    VARCHAR(40)  NOT NULL,
    id_actor    BIGINT       NULL,
    actor       VARCHAR(60)  NOT NULL,
    rol         VARCHAR(20)  NULL,
    accion      VARCHAR(60)  NOT NULL,
    entidad     VARCHAR(60)  NULL,
    id_entidad  VARCHAR(60)  NULL,
    detalle     VARCHAR(500) NULL,
    ip          VARCHAR(45)  NULL,
    fecha       DATETIME     NOT NULL,
    PRIMARY KEY (id_bitacora),
    INDEX ix_bitacora_fecha (fecha),
    INDEX ix_bitacora_servicio_fecha (servicio, fecha),
    INDEX ix_bitacora_accion_fecha (accion, fecha),
    INDEX ix_bitacora_actor (actor)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE evento_procesado (
    id_evento    CHAR(36)    NOT NULL,
    tipo         VARCHAR(60) NOT NULL,
    procesado_en DATETIME    NOT NULL,
    PRIMARY KEY (id_evento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- auth-service · v2: cuentas de clientes del portal (válido en MariaDB 10.4 y MySQL 8.4).
-- Tabla nueva: no toca administradores ni bitácora. El DNI es el vínculo con las reservas (reservas-service).

CREATE TABLE cuenta_cliente (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    dni               CHAR(8)      NOT NULL,
    nombres           VARCHAR(60)  NOT NULL,
    apellidos         VARCHAR(60)  NOT NULL,
    correo            VARCHAR(100) NOT NULL,
    telefono          CHAR(9)      NOT NULL,
    contrasena_hash   VARCHAR(100) NOT NULL,
    estado            ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
    intentos_fallidos INT          NOT NULL DEFAULT 0,
    bloqueado_hasta   DATETIME     NULL,
    creado_en         DATETIME     NOT NULL,
    ultimo_acceso     DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_cuenta_cliente_dni UNIQUE (dni),
    CONSTRAINT uq_cuenta_cliente_correo UNIQUE (correo),
    CONSTRAINT ck_cuenta_cliente_intentos CHECK (intentos_fallidos >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

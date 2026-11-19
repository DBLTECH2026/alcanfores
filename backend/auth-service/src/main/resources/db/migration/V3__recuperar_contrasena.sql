-- auth-service · v3: recuperar la contraseña con un código por correo o WhatsApp (válido en MariaDB 10.4 y MySQL 8.4).
-- 1) Celular opcional de los usuarios del panel (para recibir el código por WhatsApp).
-- 2) Códigos de recuperación: solo se guarda el hash del código y el del permiso para cambiar la contraseña.

ALTER TABLE administrador ADD COLUMN telefono CHAR(9) NULL AFTER correo;

CREATE TABLE codigo_recuperacion (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tipo_cuenta     ENUM('CLIENTE','ADMIN') NOT NULL,
    id_cuenta       BIGINT       NOT NULL,
    canal           ENUM('CORREO','WHATSAPP') NOT NULL,
    destino         VARCHAR(120) NOT NULL COMMENT 'correo o celular al que se envió',
    codigo_hash     CHAR(64)     NOT NULL COMMENT 'HMAC-SHA256 del código de 6 dígitos',
    intentos        INT          NOT NULL DEFAULT 0,
    expira_en       DATETIME     NOT NULL,
    estado          ENUM('PENDIENTE','VERIFICADO','USADO','ANULADO') NOT NULL DEFAULT 'PENDIENTE',
    token_hash      CHAR(64)     NULL COMMENT 'SHA-256 del permiso de un solo uso para restablecer',
    token_expira_en DATETIME     NULL,
    creado_en       DATETIME     NOT NULL,
    usado_en        DATETIME     NULL,
    ip              VARCHAR(45)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_codigo_recuperacion_token UNIQUE (token_hash),
    CONSTRAINT ck_codigo_recuperacion_intentos CHECK (intentos >= 0),
    INDEX ix_codigo_recuperacion_cuenta (tipo_cuenta, id_cuenta, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

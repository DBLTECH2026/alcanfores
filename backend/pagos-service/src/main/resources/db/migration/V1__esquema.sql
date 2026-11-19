-- pagos-service · esquema inicial (MySQL 8.4 y MariaDB 10.4)
-- Comprobantes SIMULADOS: no se envían a SUNAT y no tienen validez tributaria (proyecto académico UTP).

CREATE TABLE pago (
    id_pago             BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_reserva      VARCHAR(12)  NOT NULL,
    id_administrador    BIGINT       NULL,
    metodo              ENUM ('YAPE','PLIN','TARJETA','MERCADO_PAGO','EFECTIVO','TARJETA_POS') NOT NULL,
    canal               ENUM ('COMPROBANTE','PASARELA','CAJA') NOT NULL,
    tipo_pago           ENUM ('ADELANTO','TOTAL','SALDO') NOT NULL,
    monto               DECIMAL(10, 2) NOT NULL,
    codigo_operacion    VARCHAR(40)  NULL,
    archivo_comprobante VARCHAR(60)  NULL,
    estado_validacion   ENUM ('PENDIENTE','VALIDADO','RECHAZADO','ANULADO','REEMBOLSADO') NOT NULL,
    observacion         VARCHAR(255) NULL,
    referencia_pasarela VARCHAR(20)  NULL,
    marca_tarjeta       VARCHAR(20)  NULL,
    ultimos4            VARCHAR(4)   NULL,
    codigo_autorizacion VARCHAR(12)  NULL,
    fecha_envio         DATETIME     NOT NULL,
    fecha_validacion    DATETIME     NULL,
    validado_por        VARCHAR(60)  NULL,
    -- Ampliación: fotografía de la reserva al registrar el pago (los servicios no comparten base de datos)
    cliente_dni         VARCHAR(8)   NOT NULL,
    cliente_nombres     VARCHAR(60)  NOT NULL,
    cliente_apellidos   VARCHAR(60)  NOT NULL,
    cliente_telefono    VARCHAR(9)   NULL,
    cliente_correo      VARCHAR(100) NULL,
    concepto            VARCHAR(160) NOT NULL,
    porcentaje_adelanto INT          NULL,
    KEY ix_pago_reserva (codigo_reserva),
    KEY ix_pago_estado (estado_validacion, fecha_envio),
    KEY ix_pago_validacion (fecha_validacion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE intento_pago (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    referencia          VARCHAR(14)  NOT NULL,
    codigo_reserva      VARCHAR(12)  NOT NULL,
    proveedor           ENUM ('BETA','MERCADO_PAGO') NOT NULL,
    tipo_pago           ENUM ('ADELANTO','TOTAL','SALDO') NOT NULL,
    monto               DECIMAL(10, 2) NOT NULL,
    estado              ENUM ('CREADO','REQUIERE_3DS','APROBADO','RECHAZADO','EXPIRADO') NOT NULL,
    motivo_rechazo      VARCHAR(160) NULL,
    id_externo          VARCHAR(80)  NULL,
    url_checkout        VARCHAR(500) NULL,
    id_pago             BIGINT       NULL,
    expira_en           DATETIME     NOT NULL,
    creado_en           DATETIME     NOT NULL,
    actualizado_en      DATETIME     NOT NULL,
    cliente_dni         VARCHAR(8)   NOT NULL,
    cliente_nombres     VARCHAR(60)  NOT NULL,
    cliente_apellidos   VARCHAR(60)  NOT NULL,
    cliente_telefono    VARCHAR(9)   NULL,
    cliente_correo      VARCHAR(100) NULL,
    concepto            VARCHAR(160) NOT NULL,
    porcentaje_adelanto INT          NULL,
    CONSTRAINT uq_intento_referencia UNIQUE (referencia),
    KEY ix_intento_reserva (codigo_reserva),
    KEY ix_intento_estado (estado, expira_en),
    CONSTRAINT fk_intento_pago FOREIGN KEY (id_pago) REFERENCES pago (id_pago)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE datos_facturacion (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_reserva     VARCHAR(12)  NOT NULL,
    id_pago            BIGINT       NULL,
    referencia_intento VARCHAR(14)  NULL,
    tipo               ENUM ('BOLETA','FACTURA') NOT NULL,
    tipo_doc           VARCHAR(1)   NOT NULL,
    num_doc            VARCHAR(11)  NOT NULL,
    nombre             VARCHAR(150) NOT NULL,
    direccion          VARCHAR(200) NULL,
    KEY ix_facturacion_reserva (codigo_reserva),
    KEY ix_facturacion_intento (referencia_intento),
    CONSTRAINT uq_facturacion_pago UNIQUE (id_pago),
    CONSTRAINT fk_facturacion_pago FOREIGN KEY (id_pago) REFERENCES pago (id_pago)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE webhook_evento (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    proveedor    ENUM ('BETA','MERCADO_PAGO') NOT NULL,
    id_externo   VARCHAR(80)  NULL,
    tipo         VARCHAR(40)  NOT NULL,
    firma_valida BOOLEAN      NOT NULL,
    payload      JSON         NULL,
    procesado    BOOLEAN      NOT NULL,
    resultado    VARCHAR(255) NULL,
    recibido_en  DATETIME     NOT NULL,
    CONSTRAINT uq_webhook_externo UNIQUE (proveedor, id_externo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Libro del proveedor simulado «Alcanfores Pay (beta)»: sus transacciones y la cola de avisos (webhooks) al comercio.
CREATE TABLE pasarela_transaccion (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_transaccion        VARCHAR(36)  NOT NULL,
    referencia            VARCHAR(14)  NOT NULL,
    metodo                ENUM ('TARJETA','YAPE') NOT NULL,
    marca                 VARCHAR(20)  NULL,
    ultimos4              VARCHAR(4)   NULL,
    estado                ENUM ('APROBADA','RECHAZADA','PENDIENTE_3DS','ANULADA') NOT NULL,
    motivo                VARCHAR(160) NULL,
    codigo_autorizacion   VARCHAR(6)   NULL,
    monto                 DECIMAL(10, 2) NOT NULL,
    id_evento             VARCHAR(36)  NULL,
    cuerpo_notificacion   TEXT         NULL,
    notificada            BOOLEAN      NOT NULL,
    intentos_notificacion INT          NOT NULL,
    ultimo_error          VARCHAR(255) NULL,
    proximo_intento       DATETIME     NULL,
    creado_en             DATETIME     NOT NULL,
    CONSTRAINT uq_transaccion UNIQUE (id_transaccion),
    KEY ix_transaccion_referencia (referencia),
    KEY ix_transaccion_aviso (notificada, proximo_intento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE serie (
    serie              VARCHAR(4) PRIMARY KEY,
    tipo               ENUM ('BOLETA','FACTURA','NOTA_CREDITO') NOT NULL,
    ultimo_correlativo INT        NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO serie (serie, tipo, ultimo_correlativo) VALUES
    ('B001', 'BOLETA', 0),
    ('F001', 'FACTURA', 0),
    ('BC01', 'NOTA_CREDITO', 0),
    ('FC01', 'NOTA_CREDITO', 0);

CREATE TABLE comprobante (
    id_comprobante      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo                ENUM ('BOLETA','FACTURA','NOTA_CREDITO') NOT NULL,
    codigo_tipo         VARCHAR(2)   NOT NULL,
    serie               VARCHAR(4)   NOT NULL,
    correlativo         INT          NOT NULL,
    fecha_emision       DATETIME     NOT NULL,
    codigo_reserva      VARCHAR(12)  NOT NULL,
    id_pago             BIGINT       NOT NULL,
    cliente_tipo_doc    VARCHAR(1)   NOT NULL,
    cliente_num_doc     VARCHAR(11)  NOT NULL,
    cliente_nombre      VARCHAR(150) NOT NULL,
    cliente_direccion   VARCHAR(200) NULL,
    descripcion         VARCHAR(255) NOT NULL,
    op_gravada          DECIMAL(10, 2) NOT NULL,
    igv                 DECIMAL(10, 2) NOT NULL,
    total               DECIMAL(10, 2) NOT NULL,
    monto_letras        VARCHAR(200) NOT NULL,
    hash                VARCHAR(64)  NOT NULL,
    cadena_qr           VARCHAR(255) NOT NULL,
    estado              ENUM ('EMITIDO','ANULADO') NOT NULL,
    estado_sunat        VARCHAR(40)  NOT NULL,
    id_referencia       BIGINT       NULL,
    motivo              VARCHAR(255) NULL,
    codigo_motivo       VARCHAR(2)   NULL,
    emisor_ruc          VARCHAR(11)  NOT NULL,
    emisor_razon_social VARCHAR(150) NOT NULL,
    emisor_direccion    VARCHAR(200) NOT NULL,
    CONSTRAINT uq_comprobante_numero UNIQUE (serie, correlativo),
    KEY ix_comprobante_reserva (codigo_reserva),
    KEY ix_comprobante_fecha (fecha_emision),
    CONSTRAINT fk_comprobante_pago FOREIGN KEY (id_pago) REFERENCES pago (id_pago),
    CONSTRAINT fk_comprobante_referencia FOREIGN KEY (id_referencia) REFERENCES comprobante (id_comprobante)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE evento_procesado (
    id_evento    CHAR(36)    NOT NULL PRIMARY KEY,
    tipo         VARCHAR(60) NOT NULL,
    procesado_en DATETIME    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

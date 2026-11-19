-- catalogo-service · esquema (válido en MySQL 8.4 y MariaDB 10.4)

CREATE TABLE cancha (
    id_cancha         BIGINT        NOT NULL AUTO_INCREMENT,
    slug              VARCHAR(80)   NOT NULL,
    nombre            VARCHAR(60)   NOT NULL,
    deporte           ENUM('FUTBOL_5','FUTBOL_6','FUTBOL_7','VOLEY','BASQUET','MULTIUSO') NOT NULL,
    superficie        VARCHAR(40)   NOT NULL,
    capacidad         INT           NOT NULL,
    descripcion       VARCHAR(255)  NOT NULL,
    descripcion_larga TEXT          NULL,
    techada           BOOLEAN       NOT NULL DEFAULT FALSE,
    iluminacion_led   BOOLEAN       NOT NULL DEFAULT FALSE,
    medidas           VARCHAR(40)   NULL,
    caracteristicas   JSON          NULL,
    precio_dia        DECIMAL(6,2)  NOT NULL,
    precio_noche      DECIMAL(6,2)  NOT NULL,
    estado            ENUM('ACTIVA','INACTIVA') NOT NULL DEFAULT 'ACTIVA',
    orden             INT           NOT NULL DEFAULT 1,
    creado_en         DATETIME      NOT NULL,
    actualizado_en    DATETIME      NOT NULL,
    PRIMARY KEY (id_cancha),
    CONSTRAINT uq_cancha_slug UNIQUE (slug),
    CONSTRAINT uq_cancha_nombre UNIQUE (nombre),
    CONSTRAINT ck_cancha_capacidad CHECK (capacidad BETWEEN 2 AND 30),
    CONSTRAINT ck_cancha_precio_dia CHECK (precio_dia BETWEEN 10.00 AND 999.00),
    CONSTRAINT ck_cancha_precio_noche CHECK (precio_noche BETWEEN 10.00 AND 999.00 AND precio_noche >= precio_dia)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_cancha_estado_orden ON cancha (estado, orden);

CREATE TABLE cancha_foto (
    id_foto    BIGINT        NOT NULL AUTO_INCREMENT,
    id_cancha  BIGINT        NOT NULL,
    archivo    VARCHAR(60)   NOT NULL,
    leyenda    VARCHAR(120)  NULL,
    orden      INT           NOT NULL DEFAULT 1,
    principal  BOOLEAN       NOT NULL DEFAULT FALSE,
    ancho      INT           NULL,
    alto       INT           NULL,
    creado_en  DATETIME      NOT NULL,
    PRIMARY KEY (id_foto),
    CONSTRAINT uq_cancha_foto_archivo UNIQUE (archivo),
    CONSTRAINT fk_cancha_foto_cancha FOREIGN KEY (id_cancha) REFERENCES cancha (id_cancha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_cancha_foto_cancha ON cancha_foto (id_cancha, orden);

CREATE TABLE resena (
    id_resena      BIGINT        NOT NULL AUTO_INCREMENT,
    id_cancha      BIGINT        NOT NULL,
    codigo_reserva VARCHAR(12)   NOT NULL,
    autor          VARCHAR(60)   NOT NULL,
    puntuacion     TINYINT       NOT NULL,
    comentario     VARCHAR(500)  NOT NULL,
    estado         ENUM('PUBLICADA','OCULTA') NOT NULL DEFAULT 'PUBLICADA',
    respuesta      VARCHAR(500)  NULL,
    respondido_en  DATETIME      NULL,
    creado_en      DATETIME      NOT NULL,
    PRIMARY KEY (id_resena),
    CONSTRAINT uq_resena_codigo_reserva UNIQUE (codigo_reserva),
    CONSTRAINT fk_resena_cancha FOREIGN KEY (id_cancha) REFERENCES cancha (id_cancha),
    CONSTRAINT ck_resena_puntuacion CHECK (puntuacion BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_resena_cancha_estado ON resena (id_cancha, estado, creado_en);

CREATE TABLE configuracion (
    clave       VARCHAR(40)   NOT NULL,
    valor       VARCHAR(500)  NOT NULL,
    descripcion VARCHAR(255)  NULL,
    PRIMARY KEY (clave)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Idempotencia de consumidores de eventos (contrato común a todos los servicios).
CREATE TABLE evento_procesado (
    id_evento    CHAR(36)     NOT NULL,
    tipo         VARCHAR(60)  NOT NULL,
    procesado_en DATETIME     NOT NULL,
    PRIMARY KEY (id_evento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

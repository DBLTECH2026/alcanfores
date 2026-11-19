-- catalogo-service · versión 2 (19-nov-2026): horario propio de cada cancha, feriados por cancha, calendario de días
-- especiales (feriados de Perú y días no laborables) y reseñas del complejo. Corre sobre una BD con datos sin perder nada
-- (válido en MySQL 8.4 y MariaDB 10.4).

-- A · Horario y feriado de cada cancha. inicio_noche toma el valor global actual de la configuración.
ALTER TABLE cancha
    ADD COLUMN duracion_turno_min INT          NOT NULL DEFAULT 60,
    ADD COLUMN inicio_noche       TIME         NOT NULL DEFAULT '18:00:00',
    ADD COLUMN feriado_modo       ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
    ADD COLUMN recargo_feriado    DECIMAL(8,2) NOT NULL DEFAULT 10.00;

ALTER TABLE cancha
    ADD CONSTRAINT ck_cancha_duracion_turno CHECK (duracion_turno_min BETWEEN 30 AND 240 AND duracion_turno_min % 15 = 0),
    ADD CONSTRAINT ck_cancha_recargo_feriado CHECK (recargo_feriado >= 0);

UPDATE cancha
   SET inicio_noche = COALESCE((SELECT CAST(valor AS TIME) FROM configuracion WHERE clave = 'inicio_noche'), '18:00:00');

-- cierre = hora en que TERMINA el último turno (medianoche se guarda 23:59:59). Si el día está cerrado se conservan las
-- horas para cuando se vuelva a abrir.
CREATE TABLE cancha_horario (
    id_cancha  BIGINT   NOT NULL,
    dia_semana INT      NOT NULL,
    abierto    BOOLEAN  NOT NULL DEFAULT TRUE,
    apertura   TIME     NULL,
    cierre     TIME     NULL,
    PRIMARY KEY (id_cancha, dia_semana),
    CONSTRAINT fk_cancha_horario_cancha FOREIGN KEY (id_cancha) REFERENCES cancha (id_cancha) ON DELETE CASCADE,
    CONSTRAINT ck_cancha_horario_dia CHECK (dia_semana BETWEEN 1 AND 7)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Semilla: canchas de fútbol 08:00–23:00, la losa multiusos 08:00–22:00; lunes a domingo, turnos de 60 min.
INSERT INTO cancha_horario (id_cancha, dia_semana, abierto, apertura, cierre)
SELECT c.id_cancha, d.dia, TRUE, '08:00:00', CASE WHEN c.deporte = 'MULTIUSO' THEN '22:00:00' ELSE '23:00:00' END
  FROM cancha c
 CROSS JOIN (SELECT 1 AS dia UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
             UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) d;

-- B · Calendario. canchas = arreglo JSON de ids (NULL = todas; solo en NO_LABORABLE).
CREATE TABLE dia_especial (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    fecha          DATE          NOT NULL,
    tipo           ENUM('FERIADO','NO_LABORABLE') NOT NULL,
    nombre         VARCHAR(120)  NOT NULL,
    canchas        JSON          NULL,
    creado_en      DATETIME      NOT NULL,
    actualizado_en DATETIME      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_dia_especial_fecha UNIQUE (fecha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Feriados nacionales de Perú 2026 y 2027 (Semana Santa: 2026 = 2 y 3 de abril; 2027 = 25 y 26 de marzo).
INSERT INTO dia_especial (fecha, tipo, nombre, canchas, creado_en, actualizado_en) VALUES
('2026-01-01', 'FERIADO', 'Año Nuevo', NULL, NOW(), NOW()),
('2026-04-02', 'FERIADO', 'Jueves Santo', NULL, NOW(), NOW()),
('2026-04-03', 'FERIADO', 'Viernes Santo', NULL, NOW(), NOW()),
('2026-05-01', 'FERIADO', 'Día del Trabajo', NULL, NOW(), NOW()),
('2026-06-07', 'FERIADO', 'Batalla de Arica y Día de la Bandera', NULL, NOW(), NOW()),
('2026-06-29', 'FERIADO', 'San Pedro y San Pablo', NULL, NOW(), NOW()),
('2026-07-23', 'FERIADO', 'Día de la Fuerza Aérea del Perú', NULL, NOW(), NOW()),
('2026-07-28', 'FERIADO', 'Fiestas Patrias', NULL, NOW(), NOW()),
('2026-07-29', 'FERIADO', 'Fiestas Patrias', NULL, NOW(), NOW()),
('2026-08-06', 'FERIADO', 'Batalla de Junín', NULL, NOW(), NOW()),
('2026-08-30', 'FERIADO', 'Santa Rosa de Lima', NULL, NOW(), NOW()),
('2026-10-08', 'FERIADO', 'Combate de Angamos', NULL, NOW(), NOW()),
('2026-11-01', 'FERIADO', 'Día de Todos los Santos', NULL, NOW(), NOW()),
('2026-12-08', 'FERIADO', 'Inmaculada Concepción', NULL, NOW(), NOW()),
('2026-12-09', 'FERIADO', 'Batalla de Ayacucho', NULL, NOW(), NOW()),
('2026-12-25', 'FERIADO', 'Navidad', NULL, NOW(), NOW()),
('2027-01-01', 'FERIADO', 'Año Nuevo', NULL, NOW(), NOW()),
('2027-03-25', 'FERIADO', 'Jueves Santo', NULL, NOW(), NOW()),
('2027-03-26', 'FERIADO', 'Viernes Santo', NULL, NOW(), NOW()),
('2027-05-01', 'FERIADO', 'Día del Trabajo', NULL, NOW(), NOW()),
('2027-06-07', 'FERIADO', 'Batalla de Arica y Día de la Bandera', NULL, NOW(), NOW()),
('2027-06-29', 'FERIADO', 'San Pedro y San Pablo', NULL, NOW(), NOW()),
('2027-07-23', 'FERIADO', 'Día de la Fuerza Aérea del Perú', NULL, NOW(), NOW()),
('2027-07-28', 'FERIADO', 'Fiestas Patrias', NULL, NOW(), NOW()),
('2027-07-29', 'FERIADO', 'Fiestas Patrias', NULL, NOW(), NOW()),
('2027-08-06', 'FERIADO', 'Batalla de Junín', NULL, NOW(), NOW()),
('2027-08-30', 'FERIADO', 'Santa Rosa de Lima', NULL, NOW(), NOW()),
('2027-10-08', 'FERIADO', 'Combate de Angamos', NULL, NOW(), NOW()),
('2027-11-01', 'FERIADO', 'Día de Todos los Santos', NULL, NOW(), NOW()),
('2027-12-08', 'FERIADO', 'Inmaculada Concepción', NULL, NOW(), NOW()),
('2027-12-09', 'FERIADO', 'Batalla de Ayacucho', NULL, NOW(), NOW()),
('2027-12-25', 'FERIADO', 'Navidad', NULL, NOW(), NOW());

-- Turnos que reservas-service mantiene generados hacia adelante.
INSERT INTO configuracion (clave, valor, descripcion)
SELECT 'dias_generados', '120', 'Días hacia adelante con turnos ya creados (14 a 365, al menos los días visibles).'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM configuracion WHERE clave = 'dias_generados');

-- E · Reseñas: las de clientes con cuenta (origen CLIENTE) y las que carga el complejo (sin código de reserva).
ALTER TABLE resena
    MODIFY codigo_reserva VARCHAR(12) NULL,
    ADD COLUMN origen ENUM('CLIENTE','COMPLEJO') NOT NULL DEFAULT 'CLIENTE',
    ADD COLUMN id_cuenta_cliente BIGINT NULL;

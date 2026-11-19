-- Reprogramar una reserva (19/11/2026): cambiar fecha, hora o cancha conservando lo pagado, desde el panel, el portal
-- del cliente o Canchita (asistente con IA). Una fila por cada cambio con el horario ANTERIOR y el NUEVO.
-- Válido en MySQL 8.4 y MariaDB 10.4.
--   turnos_*: ids de los turnos separados por comas («101,102»); horario_*: el texto que vio el cliente
--   («7:00–9:00 p. m.»). actor: CLIENTE (portal), CANCHITA (IA por WhatsApp o web) o PANEL (ADMIN o RECEPCION).
--   El cliente y Canchita pueden cambiar UNA vez por reserva (se cuentan sus filas); el panel no tiene ese límite.
-- Además: reserva.telefono_contacto (celular de cada reserva, para «reservas por cliente») y reserva.monto_en_revision.
CREATE TABLE reserva_reprogramacion (
    id_reprogramacion    BIGINT        NOT NULL AUTO_INCREMENT,
    id_reserva           BIGINT        NOT NULL,
    id_cancha_anterior   BIGINT        NOT NULL,
    cancha_anterior      VARCHAR(60)   NOT NULL,
    fecha_anterior       DATE          NOT NULL,
    hora_inicio_anterior TIME          NOT NULL,
    hora_fin_anterior    TIME          NOT NULL,
    horario_anterior     VARCHAR(120)  NOT NULL,
    turnos_anteriores    VARCHAR(200)  NOT NULL,
    total_anterior       DECIMAL(10,2) NOT NULL,
    id_cancha_nueva      BIGINT        NOT NULL,
    cancha_nueva         VARCHAR(60)   NOT NULL,
    fecha_nueva          DATE          NOT NULL,
    hora_inicio_nueva    TIME          NOT NULL,
    hora_fin_nueva       TIME          NOT NULL,
    horario_nuevo        VARCHAR(120)  NOT NULL,
    turnos_nuevos        VARCHAR(200)  NOT NULL,
    total_nuevo          DECIMAL(10,2) NOT NULL,
    actor                ENUM('CLIENTE','CANCHITA','PANEL') NOT NULL,
    usuario              VARCHAR(60)   NULL,
    motivo               VARCHAR(200)  NULL,
    creado_en            DATETIME      NOT NULL,
    PRIMARY KEY (id_reprogramacion),
    CONSTRAINT fk_reprogramacion_reserva FOREIGN KEY (id_reserva) REFERENCES reserva (id_reserva),
    INDEX ix_reprogramacion_reserva (id_reserva, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Celular con el que se hizo CADA reserva (normalizado a 9 dígitos). El cliente es uno por DNI y su teléfono se
-- sobrescribe en cada reserva nueva (también en la web, sin verificar nada): Canchita reconoce al cliente por DNI +
-- celular comparando con ESTE dato y los avisos de la reserva salen a este número. Las existentes toman el del cliente.
ALTER TABLE reserva ADD COLUMN telefono_contacto CHAR(9) NULL AFTER id_cliente;
UPDATE reserva r JOIN cliente c ON c.id_cliente = r.id_cliente SET r.telefono_contacto = c.telefono;
ALTER TABLE reserva MODIFY telefono_contacto CHAR(9) NOT NULL;
CREATE INDEX ix_reserva_contacto ON reserva (telefono_contacto);

-- Monto del comprobante que el complejo está validando (lo trae pago.registrado; 0 si no hay ninguno). Cuenta como
-- dinero del cliente al cambiar de horario: el adelanto se recalcula en cada cambio y ya no sirve para saberlo.
ALTER TABLE reserva ADD COLUMN monto_en_revision DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER monto_pagado;
UPDATE reserva SET monto_en_revision = monto_adelanto WHERE estado_pago = 'EN_REVISION';

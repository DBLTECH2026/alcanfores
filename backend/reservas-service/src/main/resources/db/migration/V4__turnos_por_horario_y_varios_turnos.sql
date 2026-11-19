-- v2 (19/11/2026) · turnos según el horario de cada cancha y reservas de VARIOS turnos.
-- Válido en MySQL 8.4 y MariaDB 10.4; corre sobre la BD con datos sin perder reservas.

-- C · Turno: feriado con recargo y el tipo de bloqueo (los manuales se respetan al regenerar; los demás los pone el sistema).
--     FUERA_DE_HORARIO: turno libre que ya no calza con el horario nuevo pero no se puede borrar porque una reserva
--     cancelada lo referencia; queda oculto en disponibilidad y agenda.
ALTER TABLE turno
    ADD COLUMN feriado      BOOLEAN      NOT NULL DEFAULT FALSE AFTER franja,
    ADD COLUMN recargo      DECIMAL(8,2) NOT NULL DEFAULT 0.00 AFTER feriado,
    ADD COLUMN tipo_bloqueo ENUM('MANUAL','FERIADO','NO_LABORABLE','CANCHA_INACTIVA','FUERA_DE_HORARIO') NULL AFTER estado;

UPDATE turno
SET tipo_bloqueo = CASE WHEN motivo_bloqueo = 'Cancha inactiva' THEN 'CANCHA_INACTIVA' ELSE 'MANUAL' END
WHERE estado = 'BLOQUEADO';

-- D · Turnos de cada reserva (1 a 8, misma cancha y fecha). reserva.id_turno sigue siendo el PRIMER turno.
--     turno_activo (columna generada + UNIQUE) = garantía de NO doble reserva por turno: un turno solo puede estar en
--     UNA reserva vigente. El código pone vigente = 0 al cancelar o expirar la reserva.
CREATE TABLE reserva_turno (
    id_reserva   BIGINT        NOT NULL,
    id_turno     BIGINT        NOT NULL,
    precio       DECIMAL(10,2) NOT NULL,
    vigente      BOOLEAN       NOT NULL DEFAULT TRUE,
    turno_activo BIGINT GENERATED ALWAYS AS (CASE WHEN vigente = 1 THEN id_turno ELSE NULL END) STORED,
    PRIMARY KEY (id_reserva, id_turno),
    CONSTRAINT uq_reserva_turno_activo UNIQUE (turno_activo),
    CONSTRAINT fk_reserva_turno_reserva FOREIGN KEY (id_reserva) REFERENCES reserva (id_reserva),
    CONSTRAINT fk_reserva_turno_turno FOREIGN KEY (id_turno) REFERENCES turno (id_turno),
    INDEX ix_reserva_turno_turno (id_turno)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Las reservas existentes tienen un solo turno: se copia con su precio (= monto_total) y su vigencia.
INSERT INTO reserva_turno (id_reserva, id_turno, precio, vigente)
SELECT id_reserva, id_turno, monto_total, CASE WHEN estado IN ('PENDIENTE','CONFIRMADA') THEN 1 ELSE 0 END
FROM reserva;

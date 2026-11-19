package pe.alcanfores.notificaciones.modelo;

import pe.alcanfores.comun.eventos.DatosReserva;

import java.math.BigDecimal;

/** Lo que se guarda en {@code recordatorio.datos} (JSON) al consumir {@code reserva.confirmada}. */
public record DatosRecordatorio(DatosReserva reserva, BigDecimal saldoPendiente) {
}

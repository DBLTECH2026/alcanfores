package pe.alcanfores.notificaciones.cliente;

import pe.alcanfores.comun.eventos.DatosReserva;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Lo que devuelve reservas-service en {@code GET /interno/reservas/{codigo}} (solo lo que este servicio usa). */
public record ReservaInterna(DatosReserva datos, String estado, String estadoPago, BigDecimal saldo, LocalDateTime venceEn) {

    public boolean cancelada() {
        return "CANCELADA".equals(estado);
    }
}

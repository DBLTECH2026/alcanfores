package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.util.Horarios;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.DiaEspecial;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Franja;
import pe.alcanfores.reservas.modelo.TipoBloqueo;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.ComplejoServicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Reglas que deciden cómo queda un turno LIBRE según el horario de su cancha y el calendario: tarifa día/noche, recargo
 * de feriado (cancha ABIERTA) o bloqueo automático (feriado con cancha CERRADA, día no laborable, cancha inactiva). Los
 * turnos con reserva y los bloqueos manuales no se tocan.
 */
@Component
@RequiredArgsConstructor
public class PlanificadorTurnos {

    public static final String MOTIVO_FUERA_DE_HORARIO = "Fuera del horario de la cancha";
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final ComplejoServicio complejo;

    /** Cómo debe quedar un turno libre: precio final (con recargo) y, si corresponde, el bloqueo automático. */
    public record Plan(Franja franja, BigDecimal precio, BigDecimal recargo, boolean feriado, TipoBloqueo bloqueo,
                       String motivo) {
    }

    /** Turnos [inicio, fin) que salen ese día según el horario de la cancha. */
    public List<Horarios.Tramo> tramos(CanchaCatalogo c, LocalDate fecha) {
        return c.tramos(fecha.getDayOfWeek(), complejo.parametros());
    }

    public Plan plan(CanchaCatalogo c, LocalDate fecha, LocalTime inicio) {
        ParametrosComplejo p = complejo.parametros();
        Franja franja = c.franja(inicio, p);
        BigDecimal base = c.precio(franja);
        DiaEspecial dia = complejo.diaEspecial(fecha).orElse(null);
        boolean feriado = dia != null && dia.esFeriado();
        if (!c.activa()) {
            return new Plan(franja, base, CERO, feriado, TipoBloqueo.CANCHA_INACTIVA, Turno.MOTIVO_CANCHA_INACTIVA);
        }
        if (dia != null && !feriado && dia.aplicaA(c.id())) {
            return new Plan(franja, base, CERO, false, TipoBloqueo.NO_LABORABLE, recortar(dia.nombre()));
        }
        if (feriado && c.cerradaEnFeriado()) {
            return new Plan(franja, base, CERO, true, TipoBloqueo.FERIADO, recortar("Feriado: " + dia.nombre()));
        }
        if (feriado) {
            BigDecimal recargo = c.recargo();
            return new Plan(franja, base.add(recargo), recargo, true, null, null);
        }
        return new Plan(franja, base, CERO, false, null, null);
    }

    /**
     * Aplica el plan a un turno: precio, recargo, feriado y franja; bloqueo automático o libre. Un turno RESERVADO no se
     * toca y uno con bloqueo MANUAL conserva su bloqueo.
     */
    public void aplicar(Turno t, Plan plan) {
        if (t.getEstado() == EstadoTurno.RESERVADO || t.getTipoBloqueo() == TipoBloqueo.MANUAL) {
            return;
        }
        t.setFranja(plan.franja());
        t.setPrecio(plan.precio());
        t.setRecargo(plan.recargo());
        t.setFeriado(plan.feriado());
        if (plan.bloqueo() != null) {
            t.bloquear(plan.bloqueo(), plan.motivo());
        } else {
            t.liberar();
        }
    }

    /**
     * Turno que acaba de quedar libre (se canceló su reserva o se quitó un bloqueo manual): toma el horario y el calendario
     * vigentes; si ya no calza con el horario de la cancha queda oculto (FUERA_DE_HORARIO).
     */
    public void liberado(Turno t) {
        t.liberar();
        CanchaCatalogo c = complejo.cancha(t.getIdCancha()).orElse(null);
        if (c == null) {
            return;
        }
        boolean calza = tramos(c, t.getFecha()).stream().anyMatch(tr -> tr.inicio().equals(t.getHoraInicio())
                && Horarios.minutos(tr.fin()) == Horarios.minutos(t.getHoraFin()));
        if (!calza) {
            t.bloquear(TipoBloqueo.FUERA_DE_HORARIO, MOTIVO_FUERA_DE_HORARIO);
            return;
        }
        aplicar(t, plan(c, t.getFecha(), t.getHoraInicio()));
    }

    static String recortar(String texto) {
        String t = texto == null ? "" : texto.strip();
        return t.length() > 120 ? t.substring(0, 120) : t;
    }
}

package pe.alcanfores.pagos.cliente;

import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.DatosReserva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Horario y cantidad de turnos de la reserva (v2) con respaldo para reservas-service v1. */
class ReservaInternaTest {

    private static DatosReserva datos(String horarioTexto, int turnos, BigDecimal pagado) {
        return new DatosReserva(1L, "ALC-7K3M9P", 2L, "Cancha 2", "FUTBOL_6", LocalDate.of(2026, 11, 20),
                LocalTime.of(9, 0), LocalTime.of(20, 0), new BigDecimal("160.00"), new BigDecimal("80.00"), pagado,
                new ClienteContacto("70123321", "Diego", "Ramos", "987654321", "d@correo.pe"), horarioTexto, turnos);
    }

    @Test
    void desdeDatosReservaConservaElHorarioDeVariosTurnos() {
        ReservaInterna r = ReservaInterna.de(datos("9:00–10:00 a. m. y 7:00–8:00 p. m.", 2, BigDecimal.ZERO));
        assertThat(r.horario()).isEqualTo("9:00–10:00 a. m. y 7:00–8:00 p. m.");
        assertThat(r.turnos()).isEqualTo(2);
        assertThat(r.estado()).isEqualTo(ReservaInterna.PENDIENTE);
        assertThat(r.saldoPendiente()).isEqualByComparingTo("160.00");
    }

    @Test
    void sinHorarioTextoUsaElRangoYSinTurnosAsumeUno() {
        ReservaInterna r = ReservaInterna.de(datos(null, 0, new BigDecimal("80.00")));
        assertThat(r.horario()).isEqualTo("9:00 a. m.–8:00 p. m.");
        assertThat(r.turnos()).isEqualTo(1);
        assertThat(r.estado()).isEqualTo(ReservaInterna.CONFIRMADA);
        ReservaInterna sinHoras = new ReservaInterna(1L, "ALC-7K3M9P", 2L, "Cancha 2", "FUTBOL_6", null, null, null,
                null, null, null, null, "PENDIENTE", "SIN_PAGO", null, null, " ", null);
        assertThat(sinHoras.horario()).isNull();
        assertThat(sinHoras.turnos()).isEqualTo(1);
    }
}

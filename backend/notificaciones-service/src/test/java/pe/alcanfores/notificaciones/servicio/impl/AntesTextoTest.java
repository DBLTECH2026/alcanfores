package pe.alcanfores.notificaciones.servicio.impl;

import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;
import pe.alcanfores.notificaciones.Datos;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** «Antes:» del aviso de cambio de horario: fecha larga, horario (el texto del evento o el rango) y la cancha anterior. */
class AntesTextoTest {

    private static ReservaReprogramadaEvento evento(String cancha, String horarioTexto, boolean conFecha) {
        return new ReservaReprogramadaEvento(UUID.randomUUID(), Datos.INICIO, Datos.reserva(), "CONFIRMADA",
                BigDecimal.ZERO, cancha, conFecha ? Datos.FECHA : null, Datos.HORA, Datos.HORA.plusHours(1), horarioTexto,
                new BigDecimal("80.00"), ReservaReprogramadaEvento.PANEL, null, "ADELANTO_PAGADO", null);
    }

    @Test
    void conHorarioYCancha() {
        assertThat(AvisoServicioImpl.antesTexto(evento("Cancha 1", "7:00–8:00 p. m.", true)))
                .isEqualTo("Viernes 20 de noviembre de 2026, 7:00–8:00 p. m. (Cancha 1)");
    }

    @Test
    void sinTextoDeHorarioSeArmaConLasHorasYSinCanchaNoLaNombra() {
        assertThat(AvisoServicioImpl.antesTexto(evento(" ", null, true)))
                .isEqualTo("Viernes 20 de noviembre de 2026, 7:00–8:00 p. m.");
    }

    @Test
    void sinFechaAnteriorQuedaVacio() {
        assertThat(AvisoServicioImpl.antesTexto(evento("Cancha 1", "7:00–8:00 p. m.", false))).isEmpty();
    }
}

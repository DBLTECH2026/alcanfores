package pe.alcanfores.notificaciones.evento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.cliente.ReservaInterna;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.modelo.Recordatorio;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Reserva de 2 turnos NO seguidos (9 a. m. y 7 p. m.): cada aviso usa el horarioTexto y el recordatorio sale antes del primero. */
class VariosTurnosTest extends PruebaIntegracion {

    private static final String CODIGO = "ALC-DSTRNS";
    private static final String HORARIO = "9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos)";
    private static final LocalDateTime PRIMER_TURNO = LocalDateTime.of(Datos.FECHA, LocalTime.of(9, 0));

    @Autowired
    private ConsumidorEventos consumidor;
    @Autowired
    private RecordatorioServicio servicio;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        Datos.reloj(LocalDateTime.of(2026, 11, 18, 12, 0));
        when(reservasCliente.constanciaPdf(anyString())).thenReturn(Datos.PDF);
    }

    private static DatosReserva reserva(String pagado) {
        return Datos.reservaDosTurnos(CODIGO, new BigDecimal(pagado));
    }

    private List<Notificacion> avisos(String evento) {
        return notificaciones.findByCodigoReservaOrderByIdAsc(CODIGO).stream()
                .filter(n -> n.getEvento().equals(evento)).toList();
    }

    private static String cuerpo(List<Notificacion> avisos, CanalNotificacion canal) {
        return avisos.stream().filter(n -> n.getCanal() == canal).findFirst().orElseThrow().getCuerpo();
    }

    @Test
    @DisplayName("Registrada, confirmada y cancelada muestran los dos turnos en el correo y en WhatsApp")
    void cadaAvisoMuestraElHorarioDeLosDosTurnos() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 18, 12, 0);
        consumidor.alRegistrarReserva(new ReservaRegistradaEvento(UUID.randomUUID(), ahora, reserva("0.00"), "WEB", "YAPE",
                ahora.plusMinutes(20)));
        consumidor.alConfirmarReserva(new ReservaConfirmadaEvento(UUID.randomUUID(), ahora, reserva("70.00"),
                new BigDecimal("70.00")));
        consumidor.alCancelarReserva(new ReservaCanceladaEvento(UUID.randomUUID(), ahora, reserva("70.00"), "CLIENTE",
                "Se lesionó un jugador del equipo", true, new BigDecimal("70.00")));

        for (String evento : List.of("reserva.registrada", "reserva.confirmada", "reserva.cancelada")) {
            List<Notificacion> a = avisos(evento);
            assertThat(a).as(evento).hasSize(2);
            assertThat(cuerpo(a, CanalNotificacion.WHATSAPP)).as(evento).contains("*Hora:* " + HORARIO);
            assertThat(cuerpo(a, CanalNotificacion.CORREO)).as(evento).contains(HORARIO);
        }
        assertThat(cuerpo(avisos("reserva.registrada"), CanalNotificacion.WHATSAPP)).contains("S/ 70.00");
    }

    @Test
    @DisplayName("El recordatorio se programa 2 h antes del PRIMER turno y su texto lleva los dos turnos")
    void recordatorioAntesDelPrimerTurno() {
        consumidor.alConfirmarReserva(new ReservaConfirmadaEvento(UUID.randomUUID(), LocalDateTime.of(2026, 11, 18, 12, 0),
                reserva("70.00"), new BigDecimal("70.00")));
        Recordatorio r = recordatorios.findByCodigoReservaAndMinutosAntes(CODIGO, 120).orElseThrow();
        assertThat(r.getInicioReserva()).isEqualTo(PRIMER_TURNO);
        assertThat(r.getEnviarEn()).isEqualTo(PRIMER_TURNO.minusHours(2));

        Datos.reloj(PRIMER_TURNO.minusHours(2));
        when(reservasCliente.consultar(CODIGO)).thenReturn(new ReservaInterna(reserva("70.00"), "CONFIRMADA",
                "ADELANTO_PAGADO", new BigDecimal("70.00"), null));
        assertThat(servicio.ejecutarPendientes().enviados()).isEqualTo(1);
        assertThat(cuerpo(avisos("recordatorio"), CanalNotificacion.WHATSAPP))
                .contains("*Hora:* " + HORARIO, "*Saldo a pagar:* S/ 70.00");
    }

    @Test
    @DisplayName("Un recordatorio guardado antes de la v2 (JSON sin horarioTexto ni cantidadTurnos) se envía igual")
    void recordatorioGuardadoSinHorarioTexto() {
        Datos.reloj(Datos.INICIO.minusDays(1));
        consumidor.alConfirmarReserva(Datos.confirmada());
        int cambiadas = jdbc.update("UPDATE recordatorio SET datos = JSON_REMOVE(datos, '$.reserva.horarioTexto', "
                + "'$.reserva.cantidadTurnos') WHERE codigo_reserva = ?", Datos.CODIGO);
        assertThat(cambiadas).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT datos FROM recordatorio WHERE codigo_reserva = ?", String.class, Datos.CODIGO))
                .doesNotContain("horarioTexto", "cantidadTurnos");
        notificaciones.deleteAll();

        Datos.reloj(Datos.INICIO.minusHours(2));
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(Datos.reservaInterna("CONFIRMADA", "40.00", null));
        assertThat(servicio.ejecutarPendientes().enviados()).isEqualTo(1);
        String wsp = notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO).stream()
                .filter(n -> n.getCanal() == CanalNotificacion.WHATSAPP).findFirst().orElseThrow().getCuerpo();
        assertThat(wsp).contains("*Hora:* 7:00–8:00 p. m.").doesNotContain("turnos");
    }
}

package pe.alcanfores.notificaciones;

import pe.alcanfores.comun.eventos.AsistenciaRegistradaEvento;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.cliente.ReservaInterna;
import pe.alcanfores.notificaciones.servicio.DatosComplejo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Datos de prueba: un cliente, una reserva y los eventos que la acompañan. */
public final class Datos {

    public static final String CODIGO = "ALC-7K3M9P";
    public static final LocalDate FECHA = LocalDate.of(2026, 11, 20);
    public static final LocalTime HORA = LocalTime.of(19, 0);
    public static final LocalDateTime INICIO = LocalDateTime.of(FECHA, HORA);
    public static final byte[] PDF = "%PDF-1.4 constancia de prueba".getBytes(StandardCharsets.US_ASCII);

    private Datos() {
    }

    public static ClienteContacto cliente() {
        return new ClienteContacto("70123321", "Diego", "Ramos Quispe", "987 654 321", "Diego.Ramos@Example.com");
    }

    public static DatosReserva reserva() {
        return reserva(CODIGO, new BigDecimal("0.00"));
    }

    public static DatosReserva reserva(String codigo, BigDecimal pagado) {
        return new DatosReserva(10L, codigo, 1L, "Cancha 1", "FUTBOL_5", FECHA, HORA, HORA.plusHours(1),
                new BigDecimal("80.00"), new BigDecimal("40.00"), pagado, cliente());
    }

    public static DatosComplejo complejo() {
        return new DatosComplejo("Complejo Deportivo Los Alcanfores", "Carretera Central km 11.5, Santa Clara, Ate",
                "Paradero Caseta, a dos cuadras de la UTP Santa Clara", "https://maps.google.com/?q=alcanfores",
                "914995187", "informes.alcanfores@gmail.com", 2, 24);
    }

    public static ReservaRegistradaEvento registrada() {
        return new ReservaRegistradaEvento(UUID.randomUUID(), LocalDateTime.of(FECHA.minusDays(1), LocalTime.of(10, 0)),
                reserva(), "WEB", "YAPE", LocalDateTime.of(FECHA.minusDays(1), LocalTime.of(10, 20)));
    }

    public static ReservaConfirmadaEvento confirmada() {
        return confirmada(CODIGO);
    }

    public static ReservaConfirmadaEvento confirmada(String codigo) {
        return new ReservaConfirmadaEvento(UUID.randomUUID(), LocalDateTime.of(FECHA.minusDays(1), LocalTime.of(10, 5)),
                reserva(codigo, new BigDecimal("40.00")), new BigDecimal("40.00"));
    }

    public static ReservaCanceladaEvento cancelada(String actor, boolean devolucion, String pagado) {
        return new ReservaCanceladaEvento(UUID.randomUUID(), LocalDateTime.of(FECHA.minusDays(1), LocalTime.of(12, 0)),
                reserva(CODIGO, new BigDecimal(pagado)), actor, "Se lesionó un jugador del equipo", devolucion,
                devolucion ? new BigDecimal(pagado) : BigDecimal.ZERO);
    }

    public static AsistenciaRegistradaEvento asistencia(String asistencia) {
        return new AsistenciaRegistradaEvento(UUID.randomUUID(), INICIO, reserva(CODIGO, new BigDecimal("80.00")), asistencia);
    }

    public static PagoRegistradoEvento pagoRegistrado(String canal) {
        return new PagoRegistradoEvento(UUID.randomUUID(), LocalDateTime.of(FECHA.minusDays(1), LocalTime.of(10, 3)), 5L,
                CODIGO, "YAPE", canal, new BigDecimal("40.00"), "ADELANTO");
    }

    public static PagoRechazadoEvento pagoRechazado() {
        return new PagoRechazadoEvento(UUID.randomUUID(), LocalDateTime.of(FECHA.minusDays(1), LocalTime.of(10, 30)), 5L,
                CODIGO, "La imagen no muestra el número de operación", "rhuaman");
    }

    public static ComprobanteEmitidoEvento comprobante(String tipo, String serieNumero) {
        return new ComprobanteEmitidoEvento(UUID.randomUUID(), LocalDateTime.of(FECHA.minusDays(1), LocalTime.of(10, 6)), 33L,
                CODIGO, tipo, serieNumero, new BigDecimal("40.00"), cliente());
    }

    public static ReservaInterna reservaInterna(String estado, String saldo, LocalDateTime venceEn) {
        return new ReservaInterna(reserva(), estado, "EN_REVISION", saldo == null ? null : new BigDecimal(saldo), venceEn);
    }

    /** Fija el reloj del sistema (Tiempo) en una hora de Lima. */
    public static void reloj(LocalDateTime momento) {
        Tiempo.fijar(Clock.fixed(momento.atZone(Tiempo.ZONA).toInstant(), Tiempo.ZONA));
    }
}

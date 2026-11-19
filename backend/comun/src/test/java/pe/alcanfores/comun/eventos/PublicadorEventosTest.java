package pe.alcanfores.comun.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Publicación de eventos en RabbitMQ (con RabbitTemplate simulado), nombres del bus y forma JSON de los eventos. */
class PublicadorEventosTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 19, 0);

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final PublicadorEventos publicador = new PublicadorEventos(rabbit);

    private static PagoValidadoEvento pagoValidado() {
        return new PagoValidadoEvento(UUID.randomUUID(), AHORA, 5L, "ALC-7K3M9P", "YAPE", "WEB",
                new BigDecimal("40.00"), "ADELANTO", "rhuaman");
    }

    @Test
    void publicaEnElExchangeDelSistemaConLaRoutingKeyIndicada() {
        PagoValidadoEvento e = pagoValidado();
        publicador.publicar(Eventos.PAGO_VALIDADO, e);
        verify(rabbit).convertAndSend(eq("alcanfores.eventos"), eq("pago.validado"), same((Object) e));
    }

    @Test
    void siRabbitFallaNoRompeLaOperacionDeNegocio() {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatCode(() -> publicador.publicar(Eventos.PAGO_VALIDADO, pagoValidado())).doesNotThrowAnyException();
    }

    @Test
    void erroresQueNoSonDeAmqpSiSePropagan() {
        doThrow(new IllegalStateException("bug")).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatThrownBy(() -> publicador.publicar(Eventos.PAGO_VALIDADO, pagoValidado()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void colaDurableConColaDeMensajesMuertos() {
        Queue q = Eventos.cola("reservas.pago-validado");
        assertThat(q.getName()).isEqualTo("reservas.pago-validado");
        assertThat(q.isDurable()).isTrue();
        assertThat(q.isExclusive()).isFalse();
        assertThat(q.isAutoDelete()).isFalse();
        assertThat(q.getArguments())
                .containsEntry("x-dead-letter-exchange", Eventos.DLX)
                .containsEntry("x-dead-letter-routing-key", "reservas.pago-validado");
    }

    @Test
    void enlaceDeColaAlExchangeConRoutingKey() {
        Queue q = Eventos.cola("notificaciones.reserva-confirmada");
        TopicExchange x = new TopicExchange(Eventos.EXCHANGE);
        Binding b = Eventos.enlace(q, x, Eventos.RESERVA_CONFIRMADA);
        assertThat(b.getDestination()).isEqualTo("notificaciones.reserva-confirmada");
        assertThat(b.getExchange()).isEqualTo("alcanfores.eventos");
        assertThat(b.getRoutingKey()).isEqualTo("reserva.confirmada");
        assertThat(b.isDestinationQueue()).isTrue();
    }

    @Test
    void nombresDelBus() {
        assertThat(Eventos.DLX).isEqualTo("alcanfores.eventos.dlx");
        assertThat(Eventos.COLA_MUERTA).isEqualTo("alcanfores.eventos.muertos");
        assertThat(Stream.of(Eventos.RESERVA_REGISTRADA, Eventos.RESERVA_CONFIRMADA, Eventos.RESERVA_CANCELADA,
                Eventos.RESERVA_ASISTENCIA, Eventos.RESERVA_REPROGRAMADA, Eventos.PAGO_REGISTRADO, Eventos.PAGO_VALIDADO,
                Eventos.PAGO_RECHAZADO, Eventos.COMPROBANTE_EMITIDO, Eventos.CANCHA_ACTUALIZADA, Eventos.CALENDARIO_ACTUALIZADO,
                Eventos.AUDITORIA_ACCION))
                .doesNotHaveDuplicates()
                .allMatch(k -> k.matches("^[a-z]+\\.[a-z]+$"));
    }

    @Test
    void reprogramadaSabeSiQuedoConfirmada() {
        DatosReserva reserva = new DatosReserva(1L, "ALC-7K3M9P", 2L, "Cancha 1", "FUTBOL_5", LocalDate.of(2026, 11, 20),
                LocalTime.of(19, 0), LocalTime.of(20, 0), new BigDecimal("80.00"), new BigDecimal("40.00"),
                BigDecimal.ZERO, null, "7:00–8:00 p. m.", 1);
        ReservaReprogramadaEvento confirmada = new ReservaReprogramadaEvento(UUID.randomUUID(), AHORA, reserva, "CONFIRMADA",
                null, null, null, null, null, null, null, ReservaReprogramadaEvento.CLIENTE, null, "ADELANTO_PAGADO", null);
        ReservaReprogramadaEvento enRevision = new ReservaReprogramadaEvento(UUID.randomUUID(), AHORA, reserva, "PENDIENTE",
                null, null, null, null, null, null, null, ReservaReprogramadaEvento.CANCHITA, null, "EN_REVISION", null);
        ReservaReprogramadaEvento sinPago = new ReservaReprogramadaEvento(UUID.randomUUID(), AHORA, reserva, "PENDIENTE",
                null, null, null, null, null, null, null, ReservaReprogramadaEvento.CLIENTE, null, "SIN_PAGO",
                AHORA.plusMinutes(30));
        assertThat(confirmada.confirmada()).isTrue();
        assertThat(confirmada.pagoEnRevision()).isFalse();
        assertThat(enRevision.confirmada()).isFalse();
        assertThat(enRevision.pagoEnRevision()).isTrue();
        assertThat(sinPago.pagoEnRevision()).isFalse();
    }

    @Test
    void nombreCompletoDelCliente() {
        assertThat(new ClienteContacto("70123321", "Rosa", "Huamán Ríos", null, null).nombreCompleto())
                .isEqualTo("Rosa Huamán Ríos");
        assertThat(new ClienteContacto("70123321", "Rosa", "", null, null).nombreCompleto()).isEqualTo("Rosa");
    }

    // ---------- Los eventos viajan como JSON: deben ir y volver sin perder datos ----------

    static Stream<EventoDominio> eventos() {
        ClienteContacto cliente = new ClienteContacto("70123321", "Rosa", "Huamán", "987654321", "rosa@correo.pe");
        DatosReserva reserva = new DatosReserva(1L, "ALC-7K3M9P", 2L, "Cancha 1", "FUTBOL", LocalDate.of(2026, 11, 20),
                LocalTime.of(19, 0), LocalTime.of(20, 0), new BigDecimal("80.00"), new BigDecimal("40.00"),
                new BigDecimal("40.00"), cliente, "7:00–8:00 p. m.", 1);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return Stream.of(
                new ReservaRegistradaEvento(id, AHORA, reserva, "WEB", "YAPE", AHORA.plusMinutes(30)),
                new ReservaConfirmadaEvento(id, AHORA, reserva, new BigDecimal("40.00")),
                new ReservaCanceladaEvento(id, AHORA, reserva, "cliente", "Lluvia", true, new BigDecimal("40.00")),
                new AsistenciaRegistradaEvento(id, AHORA, reserva, "ASISTIO"),
                new ReservaReprogramadaEvento(id, AHORA, reserva, "CONFIRMADA", new BigDecimal("40.00"), "Cancha 2",
                        LocalDate.of(2026, 11, 19), LocalTime.of(18, 0), LocalTime.of(19, 0), "6:00–7:00 p. m.",
                        new BigDecimal("70.00"), ReservaReprogramadaEvento.PANEL, "Mantenimiento de la cancha 2",
                        "SIN_PAGO", AHORA.plusMinutes(30)),
                new PagoRegistradoEvento(id, AHORA, 5L, "ALC-7K3M9P", "YAPE", "WEB", new BigDecimal("40.00"), "ADELANTO"),
                new PagoValidadoEvento(id, AHORA, 5L, "ALC-7K3M9P", "YAPE", "WEB", new BigDecimal("40.00"), "ADELANTO", "rhuaman"),
                new PagoRechazadoEvento(id, AHORA, 5L, "ALC-7K3M9P", "Voucher ilegible", "rhuaman"),
                new ComprobanteEmitidoEvento(id, AHORA, 9L, "ALC-7K3M9P", "BOLETA", "B001-00000001",
                        new BigDecimal("80.00"), cliente),
                new CanchaActualizadaEvento(id, AHORA, 2L, "Cancha 1", "FUTBOL", new BigDecimal("80.00"),
                        new BigDecimal("100.00"), "ACTIVA"),
                new CanchaActualizadaEvento(id, AHORA, 2L, "Cancha 1", "FUTBOL", new BigDecimal("80.00"),
                        new BigDecimal("100.00"), "ACTIVA", 90, LocalTime.of(18, 0),
                        List.of(new HorarioDia(1, true, LocalTime.of(8, 0), LocalTime.of(23, 0)),
                                new HorarioDia(7, false, null, null)), "ABIERTA", new BigDecimal("10.00")),
                new CalendarioActualizadoEvento(id, AHORA, LocalDate.of(2026, 12, 8), CalendarioActualizadoEvento.NO_LABORABLE,
                        "Mantenimiento del grass", List.of(1L, 3L), false),
                new AccionAuditadaEvento(id, AHORA, "reservas-service", 7L, "rhuaman", "ADMIN", "VALIDAR_PAGO", "pago",
                        "5", "S/ 40.00", "203.0.113.5"));
    }

    @ParameterizedTest
    @MethodSource("eventos")
    void cadaEventoSeSerializaYDeserializaIgual(EventoDominio evento) throws Exception {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String texto = json.writeValueAsString(evento);
        assertThat(texto).contains("\"idEvento\":\"00000000-0000-0000-0000-000000000001\"")
                .contains("\"ocurridoEn\":\"2026-11-20T19:00:00\"");
        Object vuelta = json.readValue(texto, evento.getClass());
        assertThat(vuelta).isEqualTo(evento);
        assertThat(((EventoDominio) vuelta).idEvento()).isEqualTo(evento.idEvento());
        assertThat(((EventoDominio) vuelta).ocurridoEn()).isEqualTo(AHORA);
    }
}

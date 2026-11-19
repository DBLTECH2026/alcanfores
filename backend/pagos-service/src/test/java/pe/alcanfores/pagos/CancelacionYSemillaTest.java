package pe.alcanfores.pagos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.cliente.ReservaSemilla;
import pe.alcanfores.pagos.evento.ConsumidorReservaCancelada;
import pe.alcanfores.pagos.servicio.CancelacionReservaServicio;
import pe.alcanfores.pagos.servicio.SemillaPagos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Compensación por reserva cancelada (anulación, reembolso simulado y nota de crédito) y semilla de pagos. */
class CancelacionYSemillaTest extends IntegracionBase {

    static final String OTRA = "ALC-PAGADA";

    @Autowired
    CancelacionReservaServicio cancelaciones;
    @Autowired
    ConsumidorReservaCancelada consumidor;
    @Autowired
    SemillaPagos semilla;

    private void pagoPendienteYape() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        mvc.perform(multipart("/api/pagos/comprobante")
                        .file(new MockMultipartFile("archivo", "c.png", "image/png", png(300, 600)))
                        .param("codigoReserva", CODIGO).param("dni", DNI).param("metodo", "PLIN")
                        .param("codigoOperacion", "55443322"))
                .andExpect(status().isCreated());
    }

    private void pagoTotalEnCaja() throws Exception {
        reservaDisponible(reserva(OTRA, "PENDIENTE", "SIN_PAGO", "0"));
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + OTRA + "\",\"tipoPago\":\"TOTAL\",\"metodo\":\"TARJETA_POS\",\"monto\":90.00}"))
                .andExpect(status().isCreated());
    }

    private static ReservaCanceladaEvento cancelada(String codigo, boolean devolucion) {
        DatosReserva d = new DatosReserva(10L, codigo, 2L, "Cancha 2", "FUTBOL_6", LocalDate.now().plusDays(3),
                LocalTime.of(19, 0), LocalTime.of(20, 0), new BigDecimal("90.00"), new BigDecimal("45.00"),
                new BigDecimal("90.00"), new ClienteContacto(DNI, "Diego", "Ramos", "987654321", "d@correo.pe"));
        return new ReservaCanceladaEvento(UUID.randomUUID(), Tiempo.ahora(), d, "CLIENTE", "Se lesionó un jugador del equipo",
                devolucion, devolucion ? new BigDecimal("90.00") : BigDecimal.ZERO);
    }

    @Test
    void cancelacionAnulaPendientesSinTocarLosCobradosSiNoHayDevolucion() throws Exception {
        pagoPendienteYape();
        consumidor.alCancelarReserva(cancelada(CODIGO, false));
        assertThat(jdbc.queryForObject("SELECT estado_validacion FROM pago WHERE codigo_reserva = ?", String.class, CODIGO))
                .isEqualTo("ANULADO");
    }

    @Test
    void cancelacionConDevolucionReembolsaYEmiteNotaDeCreditoUnaSolaVez() throws Exception {
        pagoTotalEnCaja();
        ReservaCanceladaEvento evento = cancelada(OTRA, true);

        assertThat(cancelaciones.procesar(evento)).isTrue();
        assertThat(cancelaciones.procesar(evento)).isFalse();

        assertThat(jdbc.queryForObject("SELECT estado_validacion FROM pago WHERE codigo_reserva = ?", String.class, OTRA))
                .isEqualTo("REEMBOLSADO");
        List<Map<String, Object>> comprobantes = jdbc.queryForList(
                "SELECT serie, correlativo, estado, codigo_motivo FROM comprobante ORDER BY id_comprobante");
        assertThat(comprobantes).hasSize(2);
        assertThat(comprobantes.get(0)).containsEntry("serie", "B001").containsEntry("estado", "ANULADO");
        assertThat(comprobantes.get(1)).containsEntry("serie", "BC01").containsEntry("codigo_motivo", "06");
        // boleta al cobrar + nota de crédito al reembolsar
        verify(publicador, times(2)).publicar(eq(Eventos.COMPROBANTE_EMITIDO), any(ComprobanteEmitidoEvento.class));
    }

    @Test
    void semillaCreaPagosCoherentesSinPublicarEventos() {
        LocalDateTime hace = Tiempo.ahora().minusDays(10);
        ClienteContacto cliente = new ClienteContacto("45678912", "Lucía", "Mendoza Rojas", "912345678", "lucia@correo.pe");
        BigDecimal total = new BigDecimal("80.00");
        BigDecimal adelanto = new BigDecimal("40.00");
        LocalDate fecha = hace.toLocalDate().plusDays(1);
        List<ReservaSemilla> lista = List.of(
                new ReservaSemilla("ALC-SEMAAA", "CONFIRMADA", "ADELANTO_PAGADO", "YAPE", total, adelanto, adelanto,
                        hace, hace.plusMinutes(15), cliente, "Cancha 1", "FUTBOL_5", fecha, LocalTime.of(18, 0), LocalTime.of(19, 0)),
                new ReservaSemilla("ALC-SEMBBB", "CONFIRMADA", "PAGADO_TOTAL", "TARJETA", total, adelanto, total,
                        hace.plusHours(1), null, cliente, "Cancha 1", "FUTBOL_5", fecha, LocalTime.of(20, 0), LocalTime.of(21, 0)),
                new ReservaSemilla("ALC-SEMCCC", "CONFIRMADA", "PAGADO_TOTAL", "EFECTIVO", total, adelanto, total,
                        hace.plusHours(2), null, cliente, null, null, null, null, null),
                new ReservaSemilla("ALC-SEMDDD", "CONFIRMADA", "ADELANTO_PAGADO", "MERCADO_PAGO", total, adelanto, adelanto,
                        hace.plusHours(3), null, cliente, "Losa multiusos", "MULTIUSO", fecha, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                new ReservaSemilla("ALC-SEMEEE", "PENDIENTE", "EN_REVISION", "PLIN", total, adelanto, BigDecimal.ZERO,
                        Tiempo.ahora().minusMinutes(5), null, cliente, null, null, null, null, null),
                new ReservaSemilla("ALC-SEMFFF", "PENDIENTE", "OBSERVADO", "YAPE", total, adelanto, BigDecimal.ZERO,
                        Tiempo.ahora().minusHours(2), null, cliente, null, null, null, null, null),
                new ReservaSemilla("ALC-SEMGGG", "PENDIENTE", "SIN_PAGO", "YAPE", total, adelanto, BigDecimal.ZERO,
                        Tiempo.ahora().minusMinutes(2), null, cliente, null, null, null, null, null));
        when(reservas.paraSemilla()).thenReturn(lista);

        assertThat(semilla.ejecutar()).isEqualTo(7);
        assertThat(semilla.ejecutar()).isZero();

        Map<String, Long> porEstado = Map.of(
                "VALIDADO", jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE estado_validacion='VALIDADO'", Long.class),
                "PENDIENTE", jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE estado_validacion='PENDIENTE'", Long.class),
                "RECHAZADO", jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE estado_validacion='RECHAZADO'", Long.class));
        assertThat(porEstado).containsEntry("VALIDADO", 5L).containsEntry("PENDIENTE", 1L).containsEntry("RECHAZADO", 1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE serie = 'F001'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE archivo_comprobante IS NOT NULL", Integer.class)).isEqualTo(3);
        // correlativos crecientes con la fecha de emisión
        List<LocalDateTime> fechas = jdbc.queryForList(
                "SELECT fecha_emision FROM comprobante WHERE serie = 'B001' ORDER BY correlativo", LocalDateTime.class);
        assertThat(fechas).isSorted();
        verify(publicador, never()).publicar(anyString(), any());
    }
}

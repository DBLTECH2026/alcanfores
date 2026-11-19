package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.reservas.evento.ConsumidorEventos;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.Turno;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consumidores de RabbitMQ (se llaman directo): saga de pago y cambios de cancha, todos idempotentes. */
class ConsumidoresTest extends PruebaIntegracion {

    @Autowired
    private ConsumidorEventos consumidor;

    @Test
    void pagoRegistradoPasaAEnRevisionYQuitaElPlazo() throws Exception {
        String codigo = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").get("codigoReserva").asText();
        consumidor.alRegistrarPago(new PagoRegistradoEvento(UUID.randomUUID(), AHORA, 7L, codigo, "YAPE", "COMPROBANTE",
                new BigDecimal("40.00"), "ADELANTO"));
        Reserva r = reservas.findByCodigoReserva(codigo).orElseThrow();
        assertThat(r.getEstadoPago()).isEqualTo(EstadoPago.EN_REVISION);
        assertThat(r.getVenceEn()).isNull();
        mvc.perform(get("/api/reservas/" + codigo).param("dni", "70123321"))
                .andExpect(jsonPath("$.linea[1].evento").value("COMPROBANTE_RECIBIDO"))
                .andExpect(jsonPath("$.puedeReenviarComprobante").value(false));
    }

    @Test
    void pagoValidadoDelAdelantoConfirmaYEsIdempotente() throws Exception {
        String codigo = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").get("codigoReserva").asText();
        PagoValidadoEvento e = new PagoValidadoEvento(UUID.randomUUID(), AHORA, 7L, codigo, "YAPE", "COMPROBANTE",
                new BigDecimal("40.00"), "ADELANTO", "mtorres");
        consumidor.alValidarPago(e);
        consumidor.alValidarPago(e);

        Reserva r = reservas.findByCodigoReserva(codigo).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(r.getEstadoPago()).isEqualTo(EstadoPago.ADELANTO_PAGADO);
        assertThat(r.getMontoPagado()).isEqualByComparingTo("40.00");
        assertThat(r.getConfirmadaEn()).isEqualTo(AHORA);
        ArgumentCaptor<ReservaConfirmadaEvento> evento = ArgumentCaptor.forClass(ReservaConfirmadaEvento.class);
        verify(publicador, times(1)).publicar(eq(Eventos.RESERVA_CONFIRMADA), evento.capture());
        assertThat(evento.getValue().saldoPendiente()).isEqualByComparingTo("40.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_procesado", Integer.class)).isEqualTo(1);

        consumidor.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 8L, codigo, "EFECTIVO", "CAJA",
                new BigDecimal("40.00"), "SALDO", "mtorres"));
        r = reservas.findByCodigoReserva(codigo).orElseThrow();
        assertThat(r.getEstadoPago()).isEqualTo(EstadoPago.PAGADO_TOTAL);
        assertThat(r.getMontoPagado()).isEqualByComparingTo("80.00");
        assertThat(r.saldo()).isEqualByComparingTo("0.00");
        verify(publicador, times(1)).publicar(eq(Eventos.RESERVA_CONFIRMADA), any());
    }

    @Test
    void pagoValidadoTotalConfirmaComoPagadoTotal() throws Exception {
        String codigo = reservarWeb(turno(3, HOY.plusDays(2), 18).getId(), "70123321").get("codigoReserva").asText();
        consumidor.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 9L, codigo, "TARJETA", "PASARELA",
                new BigDecimal("120.00"), "TOTAL", "Pasarela"));
        Reserva r = reservas.findByCodigoReserva(codigo).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(r.getEstadoPago()).isEqualTo(EstadoPago.PAGADO_TOTAL);
    }

    @Test
    void pagoRechazadoDejaObservadoConNuevoPlazo() throws Exception {
        String lejano = reservarWeb(turno(1, HOY.plusDays(2), 19).getId(), "70123321").get("codigoReserva").asText();
        String cercano = reservarWeb(turno(1, HOY, 15).getId(), "70123322").get("codigoReserva").asText();
        consumidor.alRechazarPago(new PagoRechazadoEvento(UUID.randomUUID(), AHORA, 1L, lejano, "La imagen está borrosa", "mtorres"));
        consumidor.alRechazarPago(new PagoRechazadoEvento(UUID.randomUUID(), AHORA, 2L, cercano, "El monto no coincide", "mtorres"));

        Reserva r1 = reservas.findByCodigoReserva(lejano).orElseThrow();
        assertThat(r1.getEstadoPago()).isEqualTo(EstadoPago.OBSERVADO);
        assertThat(r1.getVenceEn()).isEqualTo(AHORA.plusHours(12));
        Reserva r2 = reservas.findByCodigoReserva(cercano).orElseThrow();
        assertThat(r2.getVenceEn()).isEqualTo(LocalDateTime.of(HOY, java.time.LocalTime.of(14, 0)));
        mvc.perform(get("/api/reservas/" + lejano).param("dni", "70123321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("OBSERVADO"))
                .andExpect(jsonPath("$.puedeReenviarComprobante").value(true))
                .andExpect(jsonPath("$.linea[1].evento").value("OBSERVADO"));
    }

    @Test
    void eventosDeReservasInexistentesSeIgnoran() {
        consumidor.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 1L, "ALC-NOEXIS", "YAPE", "COMPROBANTE",
                BigDecimal.TEN, "ADELANTO", "x"));
        verify(publicador, never()).publicar(eq(Eventos.RESERVA_CONFIRMADA), any());
    }

    @Test
    void canchaActualizadaRepreciaBloqueaYDesbloqueaTurnosFuturos() throws Exception {
        Turno libre = turno(1, HOY.plusDays(1), 19);
        Turno ocupado = turno(1, HOY.plusDays(1), 20);
        reservarWeb(ocupado.getId(), "70123321");

        consumidor.alActualizarCancha(new CanchaActualizadaEvento(UUID.randomUUID(), AHORA, 1L, "Cancha 1 · Fútbol 5",
                "FUTBOL_5", new BigDecimal("65.00"), new BigDecimal("95.00"), "ACTIVA"));
        assertThat(turnos.findById(libre.getId()).orElseThrow().getPrecio()).isEqualByComparingTo("95.00");
        assertThat(turnos.findById(ocupado.getId()).orElseThrow().getPrecio()).isEqualByComparingTo("80.00");
        assertThat(complejo.cancha(1L).orElseThrow().precioNoche()).isEqualByComparingTo("95.00");

        UUID idInactiva = UUID.randomUUID();
        CanchaActualizadaEvento inactiva = new CanchaActualizadaEvento(idInactiva, AHORA, 1L, "Cancha 1 · Fútbol 5",
                "FUTBOL_5", new BigDecimal("65.00"), new BigDecimal("95.00"), "INACTIVA");
        consumidor.alActualizarCancha(inactiva);
        Turno bloqueado = turnos.findById(libre.getId()).orElseThrow();
        assertThat(bloqueado.getEstado()).isEqualTo(EstadoTurno.BLOQUEADO);
        assertThat(bloqueado.getMotivoBloqueo()).isEqualTo("Cancha inactiva");
        assertThat(turnos.findById(ocupado.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.RESERVADO);

        consumidor.alActualizarCancha(new CanchaActualizadaEvento(UUID.randomUUID(), AHORA, 1L, "Cancha 1 · Fútbol 5",
                "FUTBOL_5", new BigDecimal("65.00"), new BigDecimal("95.00"), "ACTIVA"));
        assertThat(turnos.findById(libre.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        // El mismo evento repetido (INACTIVA) ya no se aplica.
        consumidor.alActualizarCancha(inactiva);
        assertThat(turnos.findById(libre.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
    }

    @Test
    void elRefrescoDelCatalogoAplicaLosCambiosDePrecio() {
        Turno t = turno(4, HOY.plusDays(1), 10);
        org.mockito.Mockito.when(catalogo.canchas()).thenReturn(java.util.List.of(
                new pe.alcanfores.reservas.cliente.CanchaCatalogo(4L, "Losa multiusos", "MULTIUSO",
                        new BigDecimal("45.00"), new BigDecimal("55.00"), "ACTIVA")));
        org.mockito.Mockito.when(catalogo.configuracion()).thenReturn(java.util.Map.of("adelanto_pct", "30", "dias_visibles", "10"));
        turnoServicio.sincronizarCatalogo();
        assertThat(turnos.findById(t.getId()).orElseThrow().getPrecio()).isEqualByComparingTo("45.00");
        assertThat(complejo.parametros().adelantoPct()).isEqualTo(30);
        assertThat(complejo.parametros().diasVisibles()).isEqualTo(10);
    }

    @Test
    void siCatalogoNoRespondeSeConservanLosValoresActuales() {
        org.mockito.Mockito.when(catalogo.configuracion()).thenThrow(new IllegalStateException("caído"));
        org.mockito.Mockito.when(catalogo.canchas()).thenThrow(new IllegalStateException("caído"));
        assertThat(complejo.refrescar()).isEmpty();
        assertThat(complejo.canchas()).hasSize(4);
        assertThat(complejo.parametros().adelantoPct()).isEqualTo(50);
    }
}

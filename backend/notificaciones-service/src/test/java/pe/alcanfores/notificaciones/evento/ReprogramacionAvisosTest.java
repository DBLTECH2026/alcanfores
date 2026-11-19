package pe.alcanfores.notificaciones.evento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.cliente.ReservaInterna;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoRecordatorio;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.modelo.Recordatorio;
import pe.alcanfores.notificaciones.modelo.TipoAdjunto;
import pe.alcanfores.notificaciones.servicio.DatosComplejo;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * reserva.reprogramada: aviso «¡Cambiamos tu horario!» por correo y WhatsApp con su tarjeta, la constancia actualizada y
 * los recordatorios movidos al nuevo inicio (solo si la reserva está confirmada). Idempotente.
 */
class ReprogramacionAvisosTest extends PruebaIntegracion {

    static final LocalDate NUEVA_FECHA = Datos.FECHA.plusDays(1);
    static final LocalDateTime NUEVO_INICIO = LocalDateTime.of(NUEVA_FECHA, LocalTime.of(20, 0));

    @Autowired
    private ConsumidorEventos consumidor;
    @Autowired
    private RecordatorioServicio recordatoriosServicio;

    @BeforeEach
    void preparar() {
        Datos.reloj(Datos.INICIO.minusDays(1));
        when(reservasCliente.constanciaPdf(anyString())).thenReturn(Datos.PDF);
    }

    /** La reserva DESPUÉS del cambio: Cancha 2 el sábado de 8 a 9 p. m. por S/ 90.00 con S/ 40.00 pagados. */
    private static DatosReserva nueva() {
        return new DatosReserva(10L, Datos.CODIGO, 2L, "Cancha 2", "FUTBOL_6", NUEVA_FECHA, LocalTime.of(20, 0),
                LocalTime.of(21, 0), new BigDecimal("90.00"), new BigDecimal("45.00"), new BigDecimal("40.00"),
                Datos.cliente(), "8:00–9:00 p. m.", 1);
    }

    /** Cuándo se confirmó la reserva (antes del cambio de horario). */
    static final LocalDateTime CONFIRMADA_EN = Datos.INICIO.minusDays(2);
    /** Cuándo se cambió el horario. */
    static final LocalDateTime CAMBIADA_EN = Datos.INICIO.minusDays(1);

    private static ReservaReprogramadaEvento reprogramada(String estado, String actor, String motivo) {
        String estadoPago = "CONFIRMADA".equals(estado) ? "ADELANTO_PAGADO" : "SIN_PAGO";
        return reprogramada(estado, estadoPago, null, actor, motivo, CAMBIADA_EN);
    }

    private static ReservaReprogramadaEvento reprogramada(String estado, String estadoPago, LocalDateTime venceEn,
                                                          String actor, String motivo, LocalDateTime ocurridoEn) {
        return new ReservaReprogramadaEvento(UUID.randomUUID(), ocurridoEn, nueva(), estado,
                new BigDecimal("50.00"), "Cancha 1", Datos.FECHA, Datos.HORA, Datos.HORA.plusHours(1), "7:00–8:00 p. m.",
                new BigDecimal("80.00"), actor, motivo, estadoPago, venceEn);
    }

    /** Recordatorios de la reserva confirmada con el horario anterior (viernes 7 p. m.). */
    private void programadosConElHorarioAnterior(Integer... minutos) {
        recordatoriosServicio.programar(Datos.reserva(Datos.CODIGO, new BigDecimal("40.00")), new BigDecimal("40.00"),
                List.of(minutos), CONFIRMADA_EN);
    }

    private List<Notificacion> avisos() {
        return notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO).stream()
                .filter(n -> "reserva.reprogramada".equals(n.getEvento())).toList();
    }

    private String cuerpo(CanalNotificacion canal) {
        return avisos().stream().filter(n -> n.getCanal() == canal).findFirst().orElseThrow().getCuerpo();
    }

    private void conMinutos(Integer... minutos) {
        DatosComplejo d = new DatosComplejo("Complejo Deportivo Los Alcanfores", "Carretera Central km 11.5, Santa Clara, Ate",
                "Paradero Caseta", "https://maps.google.com/?q=alcanfores", "914995187", "c@x.pe", List.of(minutos), 24);
        when(complejoServicio.datos()).thenReturn(d);
        when(complejoServicio.actualizados()).thenReturn(d);
    }

    @Test
    @DisplayName("Confirmada: aviso por los dos canales con antes → ahora, montos, tarjeta y constancia")
    void avisoConAntesYAhora() {
        consumidor.alReprogramarReserva(reprogramada("CONFIRMADA", "CLIENTE", null));

        assertThat(avisos()).hasSize(2).allSatisfy(n -> {
            assertThat(n.getAsunto()).isEqualTo("Cambiamos tu horario · ALC-7K3M9P · 21/11/2026 8:00 p. m.");
            assertThat(n.getAdjuntos()).extracting(Adjunto::tipo).containsExactly(TipoAdjunto.IMAGEN, TipoAdjunto.CONSTANCIA);
            assertThat(n.getAdjuntos().get(0).referencia()).isEqualTo("reserva-reprogramada");
        });
        assertThat(cuerpo(CanalNotificacion.WHATSAPP)).isEqualTo("""
                *¡Cambiamos tu horario!*

                Hola, Diego. Tu nueva fecha y hora ya están separadas.

                *Código:* ALC-7K3M9P
                *Antes:* Viernes 20 de noviembre de 2026, 7:00–8:00 p. m. (Cancha 1)

                *Ahora*
                *Cancha:* Cancha 2 · Fútbol 6
                *Fecha:* Sábado 21 de noviembre de 2026
                *Hora:* 8:00–9:00 p. m.
                *Total:* S/ 90.00
                *Pagado:* S/ 40.00
                *Saldo:* S/ 50.00

                Te enviaremos el recordatorio antes del nuevo horario.
                Te adjuntamos la constancia actualizada.
                Ver tu reserva: http://localhost:5186/mi-reserva?codigo=ALC-7K3M9P

                Complejo Deportivo Los Alcanfores""");
        assertThat(cuerpo(CanalNotificacion.CORREO)).contains("¡Cambiamos tu horario!", "Horario anterior",
                "Viernes 20 de noviembre de 2026, 7:00–8:00 p. m. (Cancha 1)", "Lo que ya pagaste se mantiene",
                "Pagado: S/ 40.00. Saldo: S/ 50.00.", "cid:tarjeta-aviso", "Ver mi reserva");
    }

    @Test
    @DisplayName("Los recordatorios pasan al nuevo inicio con la lista vigente (también el ya enviado) y es idempotente")
    void recordatoriosMovidos() {
        programadosConElHorarioAnterior(120, 30);
        conMinutos(120, 15);
        ReservaReprogramadaEvento e = reprogramada("CONFIRMADA", "PANEL", "Mantenimiento de la cancha 1");

        consumidor.alReprogramarReserva(e);
        consumidor.alReprogramarReserva(e);

        assertThat(avisos()).hasSize(2);
        assertThat(eventosProcesados.existsById(e.idEvento().toString())).isTrue();
        Recordatorio r120 = recordatorios.findByCodigoReservaAndMinutosAntes(Datos.CODIGO, 120).orElseThrow();
        assertThat(r120.getEstado()).isEqualTo(EstadoRecordatorio.PROGRAMADO);
        assertThat(r120.getInicioReserva()).isEqualTo(NUEVO_INICIO);
        assertThat(r120.getEnviarEn()).isEqualTo(NUEVO_INICIO.minusHours(2));
        assertThat(r120.getDatos().reserva().nombreCancha()).isEqualTo("Cancha 2");
        assertThat(r120.getDatos().saldoPendiente()).isEqualByComparingTo("50.00");
        Recordatorio r15 = recordatorios.findByCodigoReservaAndMinutosAntes(Datos.CODIGO, 15).orElseThrow();
        assertThat(r15.getEstado()).isEqualTo(EstadoRecordatorio.PROGRAMADO);
        assertThat(r15.getEnviarEn()).isEqualTo(NUEVO_INICIO.minusMinutes(15));
        // El de 30 minutos ya no está en la lista vigente del complejo: queda cancelado.
        Recordatorio r30 = recordatorios.findByCodigoReservaAndMinutosAntes(Datos.CODIGO, 30).orElseThrow();
        assertThat(r30.getEstado()).isEqualTo(EstadoRecordatorio.CANCELADO);
        assertThat(r30.getDetalle()).isEqualTo("Ya no está en la lista de recordatorios del complejo.");
        // El motivo del panel es una nota interna: no sale en el WhatsApp ni en el correo del cliente.
        assertThat(cuerpo(CanalNotificacion.WHATSAPP)).doesNotContain("Motivo", "Mantenimiento de la cancha 1");
        assertThat(cuerpo(CanalNotificacion.CORREO)).doesNotContain("Motivo", "Mantenimiento de la cancha 1");
    }

    @Test
    @DisplayName("Un recordatorio ya enviado del horario anterior se vuelve a programar para el nuevo")
    void recordatorioEnviadoSeReprograma() {
        programadosConElHorarioAnterior(120);
        Recordatorio r = recordatorios.findByCodigoReservaAndMinutosAntes(Datos.CODIGO, 120).orElseThrow();
        recordatorios.tomarParaEnvio(r.getId(), Datos.INICIO.minusHours(2));

        consumidor.alReprogramarReserva(reprogramada("CONFIRMADA", "CLIENTE", null));

        Recordatorio movido = recordatorios.findById(r.getId()).orElseThrow();
        assertThat(movido.getEstado()).isEqualTo(EstadoRecordatorio.PROGRAMADO);
        assertThat(movido.getEnviadoEn()).isNull();
        assertThat(movido.getEnviarEn()).isEqualTo(NUEVO_INICIO.minusHours(2));
    }

    @Test
    @DisplayName("Pendiente de pago: aviso con el adelanto nuevo y el plazo, y NO crea recordatorios")
    void pendienteSinRecordatorios() {
        LocalDateTime vence = Datos.INICIO.minusDays(1).plusMinutes(30);
        consumidor.alReprogramarReserva(reprogramada("PENDIENTE", "SIN_PAGO", vence, "CANCHITA", null, CAMBIADA_EN));

        assertThat(avisos()).hasSize(2);
        assertThat(cuerpo(CanalNotificacion.WHATSAPP))
                .contains("*Adelanto para confirmar:* S/ 45.00", "*Plazo para pagar:* 7:30 p. m. de hoy")
                .doesNotContain("Te enviaremos el recordatorio", "Estamos validando tu comprobante");
        assertThat(cuerpo(CanalNotificacion.CORREO)).contains("Plazo para pagar: 7:30 p. m. de hoy",
                "Paga el adelanto de S/ 45.00 para confirmar tu nuevo horario.");
        assertThat(recordatorios.findByCodigoReservaOrderByMinutosAntesDesc(Datos.CODIGO)).isEmpty();
    }

    @Test
    @DisplayName("Pendiente sin plazo (reserva del panel): pide el adelanto sin inventar un plazo")
    void pendienteSinPlazo() {
        consumidor.alReprogramarReserva(reprogramada("PENDIENTE", "OBSERVADO", null, "PANEL", "Cambio pedido", CAMBIADA_EN));

        assertThat(cuerpo(CanalNotificacion.WHATSAPP))
                .contains("*Adelanto para confirmar:* S/ 45.00", "Puedes pagarlo en el complejo o desde tu reserva en línea.")
                .doesNotContain("Plazo para pagar");
        assertThat(cuerpo(CanalNotificacion.CORREO))
                .contains("Paga el adelanto de S/ 45.00 en el complejo o desde tu reserva en línea para confirmarla.");
    }

    @Test
    @DisplayName("Comprobante en revisión: no le pide pagar otra vez, le dice que lo estamos validando")
    void pendienteConComprobanteEnRevision() {
        consumidor.alReprogramarReserva(reprogramada("PENDIENTE", "EN_REVISION", null, "CLIENTE", null, CAMBIADA_EN));

        assertThat(cuerpo(CanalNotificacion.WHATSAPP))
                .contains("Estamos validando tu comprobante: te avisaremos cuando tu reserva quede confirmada.")
                .doesNotContain("Adelanto para confirmar", "Plazo para pagar", "Te enviaremos el recordatorio");
        assertThat(cuerpo(CanalNotificacion.CORREO)).contains("Comprobante en revisión").doesNotContain("Pendiente de pago");
        assertThat(recordatorios.findByCodigoReservaOrderByMinutosAntesDesc(Datos.CODIGO)).isEmpty();
    }

    @Test
    @DisplayName("Si la confirmación (más vieja) llega después del cambio de horario, los recordatorios no vuelven atrás")
    void confirmacionTardiaNoDevuelveAlHorarioViejo() {
        conMinutos(120);
        consumidor.alReprogramarReserva(reprogramada("CONFIRMADA", "CLIENTE", null));

        // reserva.confirmada trae los datos VIEJOS (viernes 7 p. m.) y ocurrió antes del cambio.
        consumidor.alConfirmarReserva(new ReservaConfirmadaEvento(UUID.randomUUID(), CONFIRMADA_EN,
                Datos.reserva(Datos.CODIGO, new BigDecimal("40.00")), new BigDecimal("40.00")));

        Recordatorio r = recordatorios.findByCodigoReservaAndMinutosAntes(Datos.CODIGO, 120).orElseThrow();
        assertThat(r.getInicioReserva()).isEqualTo(NUEVO_INICIO);
        assertThat(r.getDatos().reserva().nombreCancha()).isEqualTo("Cancha 2");
        assertThat(r.getDatosAl()).isEqualTo(CAMBIADA_EN);
        // El aviso de la confirmación igual sale (es otro aviso); solo los recordatorios se quedan.
        assertThat(notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO))
                .extracting(Notificacion::getEvento).contains("reserva.confirmada", "reserva.reprogramada");
    }

    @Test
    @DisplayName("Un cambio de horario más viejo que el último aplicado no mueve los recordatorios")
    void cambioViejoSeIgnora() {
        conMinutos(120);
        consumidor.alReprogramarReserva(reprogramada("CONFIRMADA", "CLIENTE", null));
        DatosReserva anterior = new DatosReserva(10L, Datos.CODIGO, 1L, "Cancha 1", "FUTBOL_5", NUEVA_FECHA,
                LocalTime.of(18, 0), LocalTime.of(19, 0), new BigDecimal("80.00"), new BigDecimal("40.00"),
                new BigDecimal("40.00"), Datos.cliente(), "6:00–7:00 p. m.", 1);
        ReservaReprogramadaEvento viejo = new ReservaReprogramadaEvento(UUID.randomUUID(), CAMBIADA_EN.minusHours(3),
                anterior, "CONFIRMADA", new BigDecimal("40.00"), "Cancha 1", Datos.FECHA, Datos.HORA,
                Datos.HORA.plusHours(1), "7:00–8:00 p. m.", new BigDecimal("80.00"), "PANEL", "Cambio", "ADELANTO_PAGADO",
                null);

        consumidor.alReprogramarReserva(viejo);

        Recordatorio r = recordatorios.findByCodigoReservaAndMinutosAntes(Datos.CODIGO, 120).orElseThrow();
        assertThat(r.getInicioReserva()).isEqualTo(NUEVO_INICIO);
        assertThat(r.getDatosAl()).isEqualTo(CAMBIADA_EN);
    }

    @Test
    @DisplayName("Si el recordatorio llega a su hora con el horario viejo, se mueve al inicio vigente que dice reservas")
    void recordatorioConHorarioViejoSeMueve() {
        programadosConElHorarioAnterior(120);
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(new ReservaInterna(nueva(), "CONFIRMADA",
                "ADELANTO_PAGADO", new BigDecimal("50.00"), null));
        Datos.reloj(Datos.INICIO.minusHours(2));

        assertThat(recordatoriosServicio.ejecutarPendientes().enviados()).isZero();

        Recordatorio r = recordatorios.findByCodigoReservaAndMinutosAntes(Datos.CODIGO, 120).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoRecordatorio.PROGRAMADO);
        assertThat(r.getInicioReserva()).isEqualTo(NUEVO_INICIO);
        assertThat(r.getDatos().reserva().nombreCancha()).isEqualTo("Cancha 2");
        assertThat(notificaciones.count()).isZero();

        Datos.reloj(NUEVO_INICIO.minusHours(2));
        assertThat(recordatoriosServicio.ejecutarPendientes().enviados()).isEqualTo(1);
        assertThat(notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO))
                .allSatisfy(n -> assertThat(n.getCuerpo()).contains("8:00–9:00 p. m."));
    }
}

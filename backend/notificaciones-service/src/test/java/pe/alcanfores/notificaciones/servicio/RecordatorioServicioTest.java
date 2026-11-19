package pe.alcanfores.notificaciones.servicio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.dto.EjecucionRecordatoriosRespuesta;
import pe.alcanfores.notificaciones.dto.RecordatorioRespuesta;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoRecordatorio;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.modelo.Recordatorio;
import pe.alcanfores.comun.util.PaginaRespuesta;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordatorioServicioTest extends PruebaIntegracion {

    @Autowired
    private AvisoServicio avisos;
    @Autowired
    private RecordatorioServicio servicio;

    private Recordatorio recordatorio() {
        return recordatorios.findByCodigoReserva(Datos.CODIGO).orElseThrow();
    }

    private void confirmarReserva() {
        Datos.reloj(Datos.INICIO.minusDays(1));
        avisos.alConfirmarReserva(Datos.confirmada());
        notificaciones.deleteAll();
    }

    @Test
    @DisplayName("Se programa al confirmar: enviar_en = inicio − horas de recordatorio (catálogo)")
    void programaConHorasDelCatalogo() {
        when(complejoServicio.datos()).thenReturn(new DatosComplejo("Complejo Deportivo Los Alcanfores", "Dir", "Ref",
                "https://maps", "914995187", "c@x.pe", 3, 24));
        confirmarReserva();

        assertThat(recordatorio().getEstado()).isEqualTo(EstadoRecordatorio.PROGRAMADO);
        assertThat(recordatorio().getEnviarEn()).isEqualTo(Datos.INICIO.minusHours(3));
    }

    @Test
    @DisplayName("Antes de su hora el job no lo envía")
    void noEnviaAntes() {
        confirmarReserva();
        Datos.reloj(Datos.INICIO.minusHours(2).minusMinutes(1));

        EjecucionRecordatoriosRespuesta r = servicio.ejecutarPendientes();

        assertThat(r.revisados()).isZero();
        assertThat(recordatorio().getEstado()).isEqualTo(EstadoRecordatorio.PROGRAMADO);
        assertThat(notificaciones.count()).isZero();
    }

    @Test
    @DisplayName("A su hora se envía por los dos canales con cancha, hora, dirección, mapa, saldo y «10 minutos antes»")
    void enviaASuHora() {
        confirmarReserva();
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(Datos.reservaInterna("CONFIRMADA", "40.00", null));
        Datos.reloj(Datos.INICIO.minusHours(2));

        EjecucionRecordatoriosRespuesta r = servicio.ejecutarPendientes();

        assertThat(r).isEqualTo(new EjecucionRecordatoriosRespuesta(1, 1, 0, 0));
        Recordatorio rec = recordatorio();
        assertThat(rec.getEstado()).isEqualTo(EstadoRecordatorio.ENVIADO);
        assertThat(rec.getEnviadoEn()).isEqualTo(Datos.INICIO.minusHours(2));
        List<Notificacion> avisosEnviados = notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO);
        assertThat(avisosEnviados).hasSize(2).allSatisfy(n -> {
            assertThat(n.getEvento()).isEqualTo("recordatorio");
            assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.SIMULADO);
            assertThat(n.getAsunto()).isEqualTo("Tu partido es en 2 horas · Cancha 1 · ALC-7K3M9P");
        });
        String wsp = avisosEnviados.stream().filter(n -> n.getCanal() == CanalNotificacion.WHATSAPP).findFirst()
                .orElseThrow().getCuerpo();
        assertThat(wsp).startsWith("*Tu partido es en 2 horas*")
                .contains("Te recordamos tu reserva de hoy.", "*Cancha:* Cancha 1 · Fútbol 5", "*Hora:* 7:00–8:00 p. m.",
                        "*Saldo a pagar:* S/ 40.00", "*Llega 10 minutos antes*",
                        "*Dirección:* Carretera Central km 11.5, Santa Clara, Ate",
                        "*Referencia:* Paradero Caseta, a dos cuadras de la UTP Santa Clara",
                        "Mapa: https://maps.google.com/?q=alcanfores")
                .endsWith("Complejo Deportivo Los Alcanfores");

        // una segunda pasada no lo repite
        assertThat(servicio.ejecutarPendientes().revisados()).isZero();
        assertThat(notificaciones.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Usa el saldo actual de reservas (si ya pagó el total, no muestra saldo)")
    void usaSaldoActual() {
        confirmarReserva();
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(Datos.reservaInterna("CONFIRMADA", "0.00", null));
        Datos.reloj(Datos.INICIO.minusHours(2));

        servicio.ejecutarPendientes();

        assertThat(notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO))
                .allSatisfy(n -> assertThat(n.getCuerpo()).doesNotContain("Saldo a pagar"));
    }

    @Test
    @DisplayName("Si reservas no responde, se envía con los datos guardados")
    void reservasCaido() {
        confirmarReserva();
        when(reservasCliente.consultar(anyString())).thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE", "caído"));
        Datos.reloj(Datos.INICIO.minusHours(1).minusMinutes(30));

        EjecucionRecordatoriosRespuesta r = servicio.ejecutarPendientes();

        assertThat(r.enviados()).isEqualTo(1);
        assertThat(notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO)).allSatisfy(n -> {
            assertThat(n.getAsunto()).startsWith("Tu partido es en 1 hora y 30 minutos");
            assertThat(n.getCuerpo()).contains("S/ 40.00");
        });
    }

    @Test
    @DisplayName("OMITIDO si al revisarlo faltan menos de 30 minutos para el inicio")
    void omitido() {
        confirmarReserva();
        Datos.reloj(Datos.INICIO.minusMinutes(20));

        EjecucionRecordatoriosRespuesta r = servicio.ejecutarPendientes();

        assertThat(r).isEqualTo(new EjecucionRecordatoriosRespuesta(1, 0, 1, 0));
        assertThat(recordatorio().getEstado()).isEqualTo(EstadoRecordatorio.OMITIDO);
        assertThat(recordatorio().getDetalle()).contains("30 minutos");
        assertThat(notificaciones.count()).isZero();
    }

    @Test
    @DisplayName("CANCELADO con reserva.cancelada: el job ya no lo envía")
    void canceladoPorEvento() {
        confirmarReserva();
        avisos.alCancelarReserva(Datos.cancelada("CLIENTE", true, "40.00"));
        notificaciones.deleteAll();
        Datos.reloj(Datos.INICIO.minusHours(2));

        EjecucionRecordatoriosRespuesta r = servicio.ejecutarPendientes();

        assertThat(r.revisados()).isZero();
        assertThat(recordatorio().getEstado()).isEqualTo(EstadoRecordatorio.CANCELADO);
        assertThat(notificaciones.count()).isZero();
    }

    @Test
    @DisplayName("Si al enviarlo reservas dice que está CANCELADA, se cancela sin enviar")
    void canceladoSegunReservas() {
        confirmarReserva();
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(Datos.reservaInterna("CANCELADA", "40.00", null));
        Datos.reloj(Datos.INICIO.minusHours(2));

        EjecucionRecordatoriosRespuesta r = servicio.ejecutarPendientes();

        assertThat(r.cancelados()).isEqualTo(1);
        assertThat(recordatorio().getEstado()).isEqualTo(EstadoRecordatorio.CANCELADO);
        assertThat(notificaciones.count()).isZero();
    }

    @Test
    @DisplayName("Una nueva confirmación reprograma el recordatorio con los datos nuevos")
    void reprograma() {
        confirmarReserva();
        avisos.alConfirmarReserva(Datos.confirmada());

        assertThat(recordatorios.count()).isEqualTo(1);
        assertThat(recordatorio().getEstado()).isEqualTo(EstadoRecordatorio.PROGRAMADO);
    }

    @Test
    @DisplayName("Ejecución manual desde el panel: queda auditada y se puede listar por estado")
    void ejecucionManualYListado() {
        confirmarReserva();
        Datos.reloj(Datos.INICIO.minusMinutes(10));

        EjecucionRecordatoriosRespuesta r = servicio.ejecutarAhora();

        assertThat(r.omitidos()).isEqualTo(1);
        verify(auditor).registrar(eq("EJECUTAR_RECORDATORIOS"), eq("recordatorio"), isNull(), contains("1 omitidos"));
        PaginaRespuesta<RecordatorioRespuesta> pagina = servicio.listar(EstadoRecordatorio.OMITIDO, PageRequest.of(0, 10));
        assertThat(pagina.contenido()).singleElement().satisfies(fila -> {
            assertThat(fila.codigoReserva()).isEqualTo(Datos.CODIGO);
            assertThat(fila.cliente()).isEqualTo("Diego Ramos Quispe");
            assertThat(fila.cancha()).isEqualTo("Cancha 1");
            assertThat(fila.estado()).isEqualTo("OMITIDO");
        });
        assertThat(servicio.listar(EstadoRecordatorio.PROGRAMADO, PageRequest.of(0, 10)).totalElementos()).isZero();
        assertThat(servicio.listar(null, PageRequest.of(0, 10)).totalElementos()).isEqualTo(1);
    }
}

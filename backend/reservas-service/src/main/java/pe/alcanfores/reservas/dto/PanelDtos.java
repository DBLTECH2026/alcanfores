package pe.alcanfores.reservas.dto;

import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Dashboard del día y reportes de reservas. */
public final class PanelDtos {

    private PanelDtos() {
    }

    public record DashboardRespuesta(LocalDate fecha, Kpis kpis, List<ProximaReserva> proximas,
                                     List<AgendaDashboardCancha> agenda, List<Alerta> alertas) {
    }

    public record Kpis(long reservasHoy, long porValidar, long confirmadasHoy, BigDecimal ocupacionHoyPct,
                       BigDecimal noShowMesPct, long reservasSemana) {
    }

    public record ProximaReserva(String codigoReserva, String cliente, String cancha, LocalDate fecha, LocalTime horaInicio,
                                 EstadoReserva estado, EstadoPago estadoPago) {
    }

    public record AgendaDashboardCancha(Long idCancha, String nombre, List<AgendaDashboardTurno> turnos) {
    }

    public record AgendaDashboardTurno(LocalTime horaInicio, EstadoTurno estado, String codigoReserva, String cliente,
                                       EstadoPago estadoPago) {
    }

    /** tipo: POR_VALIDAR, OBSERVADO, POR_VENCER, SALDO_PENDIENTE, ASISTENCIA_PENDIENTE. */
    public record Alerta(String tipo, String mensaje, String codigoReserva) {
    }

    public record ReporteReservas(LocalDate desde, LocalDate hasta, Totales totales, List<PorCancha> porCancha,
                                  List<PorDia> porDia, List<PorHora> porHora, List<PorOrigen> porOrigen,
                                  Cancelaciones cancelaciones, List<TopCliente> topClientes) {
    }

    public record Totales(long registradas, long confirmadas, long canceladas, long pendientes, long asistidas,
                          long noAsistio, BigDecimal tasaNoShowPct, BigDecimal ocupacionPct) {
    }

    public record PorCancha(Long idCancha, String cancha, long reservas, BigDecimal ocupacionPct) {
    }

    public record PorDia(LocalDate fecha, long registradas, long confirmadas, long canceladas) {
    }

    public record PorHora(String hora, long reservas) {
    }

    public record PorOrigen(String origen, long reservas) {
    }

    public record Cancelaciones(Map<String, Long> porActor, List<MotivoFrecuente> motivosFrecuentes) {
    }

    public record MotivoFrecuente(String motivo, long veces) {
    }

    public record TopCliente(String dni, String nombre, long reservas, long asistidas) {
    }
}

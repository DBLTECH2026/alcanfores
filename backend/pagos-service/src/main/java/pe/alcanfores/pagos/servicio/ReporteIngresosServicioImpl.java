package pe.alcanfores.pagos.servicio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.dto.ReporteIngresosRespuesta;
import pe.alcanfores.pagos.dto.ReporteIngresosRespuesta.Dia;
import pe.alcanfores.pagos.dto.ReporteIngresosRespuesta.Grupo;
import pe.alcanfores.pagos.modelo.CanalPago;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.TipoComprobante;
import pe.alcanfores.pagos.modelo.TipoPago;
import pe.alcanfores.pagos.repositorio.ComprobanteRepositorio;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReporteIngresosServicioImpl implements ReporteIngresosServicio {

    static final int MAXIMO_DIAS = 366;

    private final PagoRepositorio pagos;
    private final ComprobanteRepositorio comprobantes;

    public ReporteIngresosServicioImpl(PagoRepositorio pagos, ComprobanteRepositorio comprobantes) {
        this.pagos = pagos;
        this.comprobantes = comprobantes;
    }

    @Override
    @Transactional(readOnly = true)
    public ReporteIngresosRespuesta generar(LocalDate desdeSolicitado, LocalDate hastaSolicitado) {
        LocalDate hasta = hastaSolicitado != null ? hastaSolicitado : Tiempo.hoy();
        LocalDate desde = desdeSolicitado != null ? desdeSolicitado : hasta.minusDays(29);
        if (desde.isAfter(hasta)) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final no puede ser anterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS) {
            throw NegocioExcepcion.validacion("desde", "El reporte admite como máximo un año de datos.");
        }
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay().minusSeconds(1);

        List<Pago> validados = pagos.findByEstadoAndFechaValidacionBetween(EstadoValidacion.VALIDADO, inicio, fin);
        List<Pago> reembolsados = pagos.findByEstadoAndFechaValidacionBetween(EstadoValidacion.REEMBOLSADO, inicio, fin);
        long rechazados = pagos.findByEstadoAndFechaValidacionBetween(EstadoValidacion.RECHAZADO, inicio, fin).size();
        // Lo reembolsado también fue un ingreso del periodo (luego devuelto): cuenta en los totales por método y canal.
        List<Pago> cobrados = Stream.concat(validados.stream(), reembolsados.stream()).toList();

        BigDecimal total = suma(cobrados);
        BigDecimal reembolsado = suma(reembolsados);
        BigDecimal ticket = cobrados.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(cobrados.size()), 2, RoundingMode.HALF_UP);

        List<Grupo> porMetodo = agrupar(cobrados, p -> p.getMetodo().name(), clave -> MetodoPago.valueOf(clave).texto());
        List<Grupo> porCanal = agrupar(cobrados, p -> p.getCanal().name(), ReporteIngresosServicioImpl::textoCanal);
        List<Grupo> porTipoPago = agrupar(cobrados, p -> p.getTipoPago().name(), ReporteIngresosServicioImpl::textoTipoPago);

        Map<LocalDate, List<Pago>> delDia = cobrados.stream()
                .collect(Collectors.groupingBy(p -> p.getFechaValidacion().toLocalDate()));
        List<Dia> porDia = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            List<Pago> l = delDia.getOrDefault(d, List.of());
            porDia.add(new Dia(d, suma(l), l.size()));
        }

        List<Comprobante> emitidos = comprobantes.findByFechaEmisionBetween(inicio, fin);
        Map<String, List<Comprobante>> porTipo = new TreeMap<>(emitidos.stream()
                .collect(Collectors.groupingBy(c -> c.getTipo().name())));
        List<Grupo> porTipoComprobante = porTipo.entrySet().stream()
                .map(e -> new Grupo(e.getKey(), TipoComprobante.valueOf(e.getKey()).nombre(),
                        e.getValue().stream().map(Comprobante::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()))
                .toList();

        OptionalDouble espera = cobrados.stream()
                .filter(p -> p.getCanal() == CanalPago.COMPROBANTE && p.getFechaEnvio() != null)
                .mapToLong(p -> Duration.between(p.getFechaEnvio(), p.getFechaValidacion()).toSeconds())
                .average();
        Double tiempoMedio = espera.isPresent()
                ? BigDecimal.valueOf(espera.getAsDouble() / 60.0).setScale(1, RoundingMode.HALF_UP).doubleValue() : null;

        return new ReporteIngresosRespuesta(desde, hasta, total, cobrados.size(), ticket, porMetodo, porCanal, porTipoPago,
                porDia, porTipoComprobante, tiempoMedio, rechazados, reembolsado);
    }

    private static List<Grupo> agrupar(List<Pago> lista, Function<Pago, String> clave, Function<String, String> texto) {
        return lista.stream().collect(Collectors.groupingBy(clave)).entrySet().stream()
                .map(e -> new Grupo(e.getKey(), texto.apply(e.getKey()), suma(e.getValue()), e.getValue().size()))
                .sorted((a, b) -> b.total().compareTo(a.total()))
                .toList();
    }

    private static BigDecimal suma(List<Pago> lista) {
        return lista.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static String textoCanal(String canal) {
        return switch (CanalPago.valueOf(canal)) {
            case COMPROBANTE -> "Comprobante Yape/Plin";
            case PASARELA -> "Pago en línea";
            case CAJA -> "Caja del complejo";
        };
    }

    private static String textoTipoPago(String tipo) {
        return switch (TipoPago.valueOf(tipo)) {
            case ADELANTO -> "Adelanto";
            case TOTAL -> "Pago total";
            case SALDO -> "Saldo";
        };
    }
}

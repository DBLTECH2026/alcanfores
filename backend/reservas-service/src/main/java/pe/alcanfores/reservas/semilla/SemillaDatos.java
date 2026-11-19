package pe.alcanfores.reservas.semilla;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.config.ReservasPropiedades;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.Cancelacion;
import pe.alcanfores.reservas.modelo.TipoBloqueo;
import pe.alcanfores.reservas.modelo.CanceladoPor;
import pe.alcanfores.reservas.modelo.Cliente;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.ReservaHistorial;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.CancelacionRepositorio;
import pe.alcanfores.reservas.repositorio.ClienteRepositorio;
import pe.alcanfores.reservas.repositorio.HistorialRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;
import pe.alcanfores.reservas.servicio.TurnoServicio;
import pe.alcanfores.reservas.servicio.impl.PagoEventosServicioImpl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Datos de demostración (solo si no hay reservas y {@code alcanfores.reservas.semilla.habilitada=true}): 25 clientes
 * ficticios con DNI de prueba (99xxxxxx, correos @example.com), ~120 reservas de los últimos 45 días y ~20 de los
 * próximos 7, con más demanda de noche y en fin de semana. Fechas relativas a {@code Tiempo.hoy()} y azar con semilla
 * fija: cada arranque limpio produce la misma demo.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class SemillaDatos implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SemillaDatos.class);
    private static final long SEMILLA_AZAR = 20261118L;
    private static final int DIAS_PASADOS = 45;
    private static final int DIAS_FUTUROS = 7;
    private static final int RESERVAS_PASADAS = 120;
    private static final int RESERVAS_FUTURAS = 20;
    private static final long ID_ADMIN = 1L;
    private static final long ID_RECEPCION = 2L;
    /** Observación de los comprobantes OBSERVADOS de la semilla: la misma que guarda la semilla de pagos-service. */
    static final String OBSERVACION_SEMILLA = "La imagen no permite leer el número de operación. Vuelve a subirla completa, por favor.";

    private static final String[][] CLIENTES = {
            {"Diego", "Ramos Quispe"}, {"Lucía", "Fernández Torres"}, {"Carlos", "Mendoza Huamán"},
            {"Valeria", "Castillo Rojas"}, {"José", "Gutiérrez Vargas"}, {"Andrea", "Salazar Chávez"},
            {"Miguel", "Paredes Flores"}, {"Camila", "Rivera Soto"}, {"Jorge", "Espinoza Lazo"},
            {"Daniela", "Vega Cárdenas"}, {"Luis Alberto", "Ríos Campos"}, {"Fiorella", "Medina Ortiz"},
            {"Renzo", "Navarro Peña"}, {"Ximena", "Acosta Delgado"}, {"Bruno", "Herrera Silva"},
            {"Mariana", "Cruz Benites"}, {"Sebastián", "Ortega Aguilar"}, {"Ana Lucía", "Morales Ramírez"},
            {"Kevin", "Palacios Zúñiga"}, {"Rosa María", "Llanos Cueva"}, {"Piero", "Villanueva Arce"},
            {"Gabriela", "Sánchez Poma"}, {"Álvaro", "Cabrera León"}, {"Natalia", "Gómez Quiroz"},
            {"Franco", "Díaz Mallqui"}};

    private static final String[] MOTIVOS_CLIENTE = {
            "Uno de los jugadores se lesionó y no completamos el equipo",
            "Cambio de planes del equipo, reprogramaremos otro día",
            "Tengo un compromiso de trabajo a esa hora",
            "No completamos el número de jugadores"};
    private static final String[] MOTIVOS_ADMIN = {
            "El cliente pidió cancelar por WhatsApp",
            "Corte de luz programado en la zona",
            "Mantenimiento de emergencia de la cancha"};

    private final ReservasPropiedades propiedades;
    private final ReservaRepositorio reservas;
    private final TurnoRepositorio turnos;
    private final ClienteRepositorio clientes;
    private final CancelacionRepositorio cancelaciones;
    private final HistorialRepositorio historial;
    private final TurnoServicio turnoServicio;
    private final ComplejoServicio complejo;
    private final TransactionTemplate transacciones;

    @Override
    public void run(String... args) {
        if (!propiedades.semilla().habilitada()) {
            return;
        }
        if (reservas.count() > 0) {
            log.info("Semilla omitida: ya hay reservas registradas");
            return;
        }
        transacciones.executeWithoutResult(estado -> sembrar());
    }

    private void sembrar() {
        LocalDateTime ahora = Tiempo.ahora();
        LocalDate hoy = ahora.toLocalDate();
        ParametrosComplejo p = complejo.parametros();
        turnoServicio.generarRango(hoy.minusDays(DIAS_PASADOS), hoy.plusDays(p.diasVisibles()), List.of());
        Contexto ctx = new Contexto(new Random(SEMILLA_AZAR), ahora, p, crearClientes(ahora));

        Set<Long> activas = new HashSet<>();
        complejo.canchas().stream().filter(CanchaCatalogo::activa).forEach(c -> activas.add(c.id()));
        List<Turno> rango = turnos.findByFechaBetweenOrderByFechaAscIdCanchaAscHoraInicioAsc(hoy.minusDays(DIAS_PASADOS),
                hoy.plusDays(DIAS_FUTUROS));
        List<Turno> pasados = rango.stream().filter(t -> activas.contains(t.getIdCancha()))
                .filter(t -> t.fin().isBefore(ahora.minusMinutes(30))).toList();
        List<Turno> futuros = rango.stream().filter(t -> activas.contains(t.getIdCancha()))
                .filter(t -> t.getEstado() == EstadoTurno.DISPONIBLE && t.inicio().isAfter(ahora.plusMinutes(90))).toList();

        int creadas = 0;
        for (Turno t : muestrear(pasados, RESERVAS_PASADAS, ctx.azar)) {
            sembrarPasada(t, ctx);
            creadas++;
        }
        List<Turno> elegidosFuturos = muestrear(futuros, RESERVAS_FUTURAS, ctx.azar);
        List<Plan> planes = planesFuturos(elegidosFuturos.size(), ctx.azar);
        for (int i = 0; i < elegidosFuturos.size(); i++) {
            sembrarFutura(elegidosFuturos.get(i), planes.get(i), ctx);
            creadas++;
        }
        int bloqueados = bloqueoDeMantenimiento(hoy.plusDays(2));
        log.info("Semilla cargada: {} clientes, {} reservas, {} turnos bloqueados por mantenimiento",
                ctx.clientes.size(), creadas, bloqueados);
    }

    /** Reserva ya jugada (o cancelada) en los últimos 45 días. */
    private void sembrarPasada(Turno t, Contexto ctx) {
        Random azar = ctx.azar;
        Cliente cliente = ctx.cliente();
        LocalDateTime inicio = t.inicio();
        if (azar.nextDouble() < 0.74) {
            Origen origen = origen(azar);
            String metodo = metodo(origen, azar);
            LocalDateTime registro = inicio.minusHours(3 + azar.nextInt(94));
            Reserva r = nueva(t, cliente, origen, metodo, registro, ctx);
            boolean asistio = azar.nextDouble() < 0.86;
            boolean pagoTotalAntes = "TARJETA".equals(metodo) && azar.nextDouble() < 0.4;
            confirmar(r, registro, pagoTotalAntes, ctx);
            if (asistio && r.saldo().signum() > 0) {
                LocalDateTime cobro = inicio.minusMinutes(5);
                hito(r, ReservaHistorial.PAGO_VALIDADO, "Pago del saldo validado: " + Dinero.formato(r.saldo())
                        + " (efectivo).", cobro);
                r.setMontoPagado(r.getMontoTotal());
                r.setEstadoPago(EstadoPago.PAGADO_TOTAL);
            }
            r.setAsistencia(asistio ? Asistencia.ASISTIO : Asistencia.NO_ASISTIO);
            hito(r, ReservaHistorial.ASISTENCIA, asistio ? "Asistencia registrada: el cliente asistió." : "El cliente no asistió.",
                    inicio.plusMinutes(asistio ? 8 : 20));
            t.setEstado(EstadoTurno.RESERVADO);
            reservas.save(r);
            return;
        }
        double quien = azar.nextDouble();
        if (quien < 0.45) {
            cancelarPagada(t, cliente, CanceladoPor.CLIENTE, inicio.minusHours(2 + azar.nextInt(70)),
                    MOTIVOS_CLIENTE[azar.nextInt(MOTIVOS_CLIENTE.length)], azar.nextDouble() < 0.6, ctx);
        } else if (quien < 0.65) {
            cancelarPagada(t, cliente, CanceladoPor.ADMINISTRADOR, inicio.minusHours(3 + azar.nextInt(45)),
                    MOTIVOS_ADMIN[azar.nextInt(MOTIVOS_ADMIN.length)], true, ctx);
        } else {
            LocalDateTime registro = inicio.minusHours(2 + azar.nextInt(46));
            Reserva r = nueva(t, cliente, Origen.WEB, azar.nextBoolean() ? "YAPE" : "PLIN", registro, ctx);
            reservas.save(r);
            cancelar(r, CanceladoPor.SISTEMA, null, ReglasReserva.MOTIVO_VENCIDA, false,
                    registro.plusMinutes(ctx.parametros.minutosParaPagar() + 1L));
        }
    }

    /** Reserva de los próximos 7 días según el plan (por validar, observada, pendiente, cancelada o confirmada). */
    private void sembrarFutura(Turno t, Plan plan, Contexto ctx) {
        Random azar = ctx.azar;
        LocalDateTime ahora = ctx.ahora;
        Cliente cliente = ctx.cliente();
        switch (plan) {
            case POR_VALIDAR -> {
                LocalDateTime registro = ahora.minusMinutes(5 + azar.nextInt(75));
                String metodo = azar.nextDouble() < 0.7 ? "YAPE" : "PLIN";
                Reserva r = nueva(t, cliente, Origen.WEB, metodo, registro, ctx);
                r.setEstadoPago(EstadoPago.EN_REVISION);
                r.setVenceEn(null);
                t.setEstado(EstadoTurno.RESERVADO);
                reservas.save(r);
                comprobanteRecibido(r, metodo, minimo(registro.plusMinutes(3 + azar.nextInt(6)), ahora));
            }
            case OBSERVADA -> {
                LocalDateTime registro = ahora.minusHours(2 + azar.nextInt(6));
                Reserva r = nueva(t, cliente, Origen.WEB, "YAPE", registro, ctx);
                r.setEstadoPago(EstadoPago.OBSERVADO);
                LocalDateTime rechazo = registro.plusMinutes(25);
                r.setVenceEn(PagoEventosServicioImpl.plazoTrasRechazo(r, rechazo, ctx.parametros.minutosParaPagar()));
                t.setEstado(EstadoTurno.RESERVADO);
                reservas.save(r);
                comprobanteRecibido(r, "YAPE", registro.plusMinutes(6));
                hito(r, ReservaHistorial.OBSERVADO, ReglasReserva.textoObservado(OBSERVACION_SEMILLA, r.getVenceEn()), rechazo);
            }
            case PENDIENTE_WEB -> {
                LocalDateTime registro = ahora.minusMinutes(3);
                Reserva r = nueva(t, cliente, Origen.WEB, "PLIN", registro, ctx);
                r.setVenceEn(minimo(ahora.plusMinutes(17), t.inicio()));
                t.setEstado(EstadoTurno.RESERVADO);
                reservas.save(r);
            }
            case PENDIENTE_TELEFONO -> {
                Reserva r = nueva(t, cliente, Origen.TELEFONICA, null, ahora.minusHours(1 + azar.nextInt(20)), ctx);
                r.setNota("Pagará el total en el complejo al llegar");
                t.setEstado(EstadoTurno.RESERVADO);
                reservas.save(r);
            }
            case CANCELADA_CLIENTE -> cancelarPagada(t, cliente, CanceladoPor.CLIENTE, ahora.minusHours(1 + azar.nextInt(10)),
                    MOTIVOS_CLIENTE[azar.nextInt(MOTIVOS_CLIENTE.length)], true, ctx);
            case CANCELADA_ADMIN -> cancelarPagada(t, cliente, CanceladoPor.ADMINISTRADOR, ahora.minusMinutes(40),
                    MOTIVOS_ADMIN[2], true, ctx);
            case CONFIRMADA -> {
                Origen origen = origen(azar);
                String metodo = metodo(origen, azar);
                LocalDateTime registro = ahora.minusHours(1 + azar.nextInt(70));
                Reserva r = nueva(t, cliente, origen, metodo, registro, ctx);
                confirmar(r, registro, azar.nextDouble() < 0.3, ctx);
                t.setEstado(EstadoTurno.RESERVADO);
                reservas.save(r);
            }
        }
    }

    private void cancelarPagada(Turno t, Cliente cliente, CanceladoPor por, LocalDateTime fechaCancelacion, String motivo,
                                boolean pagada, Contexto ctx) {
        LocalDateTime registro = minimo(fechaCancelacion.minusHours(1 + ctx.azar.nextInt(48)), fechaCancelacion.minusMinutes(30));
        Origen origen = por == CanceladoPor.CLIENTE ? Origen.WEB : origen(ctx.azar);
        Reserva r = nueva(t, cliente, origen, metodo(origen, ctx.azar), registro, ctx);
        if (pagada) {
            confirmar(r, registro, false, ctx);
        }
        reservas.save(r);
        long horasAntes = Duration.between(fechaCancelacion, t.inicio()).toHours();
        boolean devolucion = pagada && (por == CanceladoPor.ADMINISTRADOR || horasAntes >= ctx.parametros.devolucionHoras());
        cancelar(r, por, por == CanceladoPor.ADMINISTRADOR ? ID_ADMIN : null, motivo, devolucion, fechaCancelacion);
    }

    private Reserva nueva(Turno t, Cliente c, Origen origen, String metodo, LocalDateTime registro, Contexto ctx) {
        Reserva r = new Reserva();
        r.setCodigoReserva(ctx.codigo());
        r.setCliente(c);
        r.setTelefonoContacto(c.getTelefono());
        r.setEstado(EstadoReserva.PENDIENTE);
        r.agregarTurno(t, Dinero.redondear(t.getPrecio()));
        r.setEstadoPago(EstadoPago.SIN_PAGO);
        r.setOrigen(origen);
        r.setMetodoPagoPrevisto(metodo);
        r.setMontoTotal(Dinero.redondear(t.getPrecio()));
        r.setMontoAdelanto(Dinero.porcentaje(t.getPrecio(), ctx.parametros.adelantoPct()));
        r.setMontoPagado(BigDecimal.ZERO.setScale(2));
        r.setFechaRegistro(registro);
        r.setVenceEn(origen == Origen.WEB ? minimo(registro.plusMinutes(ctx.parametros.minutosParaPagar()), t.inicio()) : null);
        r.setRegistradoPor(origen == Origen.WEB ? null : (ctx.azar.nextDouble() < 0.8 ? ID_RECEPCION : ID_ADMIN));
        r.setEsSemilla(true);
        reservas.save(r);
        String texto = switch (origen) {
            case WEB -> "Reserva registrada por la web. Paga el adelanto de " + Dinero.formato(r.getMontoAdelanto())
                    + " antes de las " + Tiempo.horaAmPm(r.getVenceEn().toLocalTime());
            case PRESENCIAL -> "Reserva registrada en el complejo por " + (r.getRegistradoPor() == ID_ADMIN ? "rhuaman" : "mtorres") + ".";
            case TELEFONICA -> "Reserva registrada por teléfono por " + (r.getRegistradoPor() == ID_ADMIN ? "rhuaman" : "mtorres") + ".";
        };
        hito(r, ReservaHistorial.REGISTRADA, texto, registro);
        return r;
    }

    /** Pago validado (adelanto o total) y confirmación, como si hubiera llegado {@code pago.validado}. */
    private void confirmar(Reserva r, LocalDateTime registro, boolean total, Contexto ctx) {
        LocalDateTime confirmada = minimo(registro.plusMinutes(r.getOrigen() == Origen.WEB ? 4 + ctx.azar.nextInt(40) : 1),
                minimo(ctx.ahora, r.inicio()));
        if (r.getOrigen() == Origen.WEB && ("YAPE".equals(r.getMetodoPagoPrevisto()) || "PLIN".equals(r.getMetodoPagoPrevisto()))) {
            comprobanteRecibido(r, r.getMetodoPagoPrevisto(), minimo(registro.plusMinutes(2), confirmada));
        }
        BigDecimal monto = total ? r.getMontoTotal() : r.getMontoAdelanto();
        r.setMontoPagado(monto);
        r.setEstadoPago(total ? EstadoPago.PAGADO_TOTAL : EstadoPago.ADELANTO_PAGADO);
        r.setEstado(EstadoReserva.CONFIRMADA);
        r.setVenceEn(null);
        r.setConfirmadaEn(confirmada);
        hito(r, ReservaHistorial.PAGO_VALIDADO, "Pago " + (total ? "total" : "del adelanto") + " validado: "
                + Dinero.formato(monto) + ".", confirmada);
        hito(r, ReservaHistorial.CONFIRMADA, r.saldo().signum() > 0
                ? "Reserva confirmada. Saldo a pagar en el complejo: " + Dinero.formato(r.saldo()) + "."
                : "Reserva confirmada y pagada en su totalidad.", confirmada);
    }

    private void cancelar(Reserva r, CanceladoPor por, Long idAdmin, String motivo, boolean devolucion, LocalDateTime fecha) {
        r.setEstado(EstadoReserva.CANCELADA);
        r.setVenceEn(null);
        reservas.save(r);
        Cancelacion c = new Cancelacion();
        c.setReserva(r);
        c.setIdAdministrador(idAdmin);
        c.setCanceladoPor(por);
        c.setMotivo(motivo);
        c.setDevolucion(devolucion);
        c.setMontoDevolucion(devolucion ? r.getMontoPagado() : BigDecimal.ZERO.setScale(2));
        c.setFechaCancelacion(fecha);
        cancelaciones.save(c);
        if (devolucion && c.getMontoDevolucion().signum() > 0) {
            r.registrarDevolucion(c.getMontoDevolucion());
            reservas.save(r);
        }
        hito(r, ReservaHistorial.CANCELADA, ReglasReserva.textoCancelacion(por, motivo, c.getMontoDevolucion()), fecha);
    }

    private void comprobanteRecibido(Reserva r, String metodo, LocalDateTime fecha) {
        hito(r, ReservaHistorial.COMPROBANTE_RECIBIDO, "Recibimos tu comprobante " + ("YAPE".equals(metodo) ? "Yape" : "Plin")
                + " por " + Dinero.formato(r.getMontoAdelanto()) + "; lo estamos validando.", fecha);
    }

    private void hito(Reserva r, String evento, String detalle, LocalDateTime fecha) {
        historial.save(new ReservaHistorial(r.getId(), evento, detalle, fecha));
    }

    private int bloqueoDeMantenimiento(LocalDate fecha) {
        int bloqueados = 0;
        for (Turno t : turnos.findByFechaOrderByIdCanchaAscHoraInicioAsc(fecha)) {
            if (t.getIdCancha() == 3L && t.getHoraInicio().getHour() < 10 && t.getEstado() == EstadoTurno.DISPONIBLE) {
                t.bloquear(TipoBloqueo.MANUAL, "Mantenimiento del grass sintético");
                bloqueados++;
            }
        }
        return bloqueados;
    }

    private List<Cliente> crearClientes(LocalDateTime ahora) {
        List<Cliente> lista = new ArrayList<>();
        for (int i = 0; i < CLIENTES.length; i++) {
            Cliente c = new Cliente();
            c.setDni(String.format("9901%04d", i + 1));
            c.setNombres(CLIENTES[i][0]);
            c.setApellidos(CLIENTES[i][1]);
            c.setTelefono(String.format("9102%05d", 1001 + i * 37));
            c.setCorreo(ascii(CLIENTES[i][0].split(" ")[0] + "." + CLIENTES[i][1].split(" ")[0]) + "@example.com");
            c.setFechaRegistro(ahora.minusDays(DIAS_PASADOS + 5L + i));
            c.setActualizadoEn(ahora.minusDays(DIAS_PASADOS + 5L + i));
            lista.add(c);
        }
        return clientes.saveAll(lista);
    }

    /** Muestreo ponderado sin reemplazo (noche y fin de semana pesan más; la Cancha 1 es la más pedida). */
    private static List<Turno> muestrear(List<Turno> candidatos, int cantidad, Random azar) {
        TreeMap<Double, Turno> llaves = new TreeMap<>(Comparator.reverseOrder());
        for (Turno t : candidatos) {
            double llave = Math.pow(azar.nextDouble(), 1.0 / peso(t));
            llaves.put(llave, t);
        }
        return llaves.values().stream().limit(cantidad).sorted(Comparator.comparing(Turno::inicio)).toList();
    }

    private static double peso(Turno t) {
        int h = t.getHoraInicio().getHour();
        double hora = h < 12 ? 1.0 : h < 18 ? 1.8 : 4.5;
        DayOfWeek d = t.getFecha().getDayOfWeek();
        double dia = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY ? 2.0 : d == DayOfWeek.FRIDAY ? 1.5 : 1.0;
        double cancha = Map.of(1L, 1.6, 2L, 1.3, 3L, 1.0, 4L, 0.7).getOrDefault(t.getIdCancha(), 1.0);
        return hora * dia * cancha;
    }

    /** Las 4 más próximas quedan por validar (para el dashboard de hoy); el resto se reparte al azar. */
    private static List<Plan> planesFuturos(int total, Random azar) {
        List<Plan> resto = new ArrayList<>(List.of(Plan.OBSERVADA, Plan.OBSERVADA, Plan.PENDIENTE_WEB,
                Plan.PENDIENTE_TELEFONO, Plan.CANCELADA_CLIENTE, Plan.CANCELADA_ADMIN));
        while (resto.size() < total - 4) {
            resto.add(Plan.CONFIRMADA);
        }
        Collections.shuffle(resto, azar);
        List<Plan> planes = new ArrayList<>();
        for (int i = 0; i < Math.min(4, total); i++) {
            planes.add(Plan.POR_VALIDAR);
        }
        planes.addAll(resto);
        return planes.subList(0, total);
    }

    private static Origen origen(Random azar) {
        double x = azar.nextDouble();
        return x < 0.65 ? Origen.WEB : x < 0.85 ? Origen.PRESENCIAL : Origen.TELEFONICA;
    }

    private static String metodo(Origen origen, Random azar) {
        double x = azar.nextDouble();
        return switch (origen) {
            case WEB -> x < 0.45 ? "YAPE" : x < 0.6 ? "PLIN" : x < 0.9 ? "TARJETA" : "MERCADO_PAGO";
            case PRESENCIAL -> x < 0.5 ? "EFECTIVO" : x < 0.8 ? "YAPE" : "TARJETA_POS";
            case TELEFONICA -> x < 0.7 ? "YAPE" : "PLIN";
        };
    }

    private static String ascii(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static LocalDateTime minimo(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private enum Plan { POR_VALIDAR, OBSERVADA, PENDIENTE_WEB, PENDIENTE_TELEFONO, CANCELADA_CLIENTE, CANCELADA_ADMIN, CONFIRMADA }

    /** Estado compartido de una corrida de la semilla. */
    private static final class Contexto {
        final Random azar;
        final LocalDateTime ahora;
        final ParametrosComplejo parametros;
        final List<Cliente> clientes;
        private final Set<String> codigos = new HashSet<>();

        Contexto(Random azar, LocalDateTime ahora, ParametrosComplejo parametros, List<Cliente> clientes) {
            this.azar = azar;
            this.ahora = ahora;
            this.parametros = parametros;
            this.clientes = clientes;
        }

        /** Los 6 primeros son clientes frecuentes (pesan el triple). */
        Cliente cliente() {
            int total = clientes.size() + 12;
            int x = azar.nextInt(total);
            return x < 18 ? clientes.get(x / 3) : clientes.get(x - 12);
        }

        String codigo() {
            String codigo;
            do {
                StringBuilder sb = new StringBuilder("ALC-");
                for (int i = 0; i < 6; i++) {
                    sb.append(CodigoReserva.ALFABETO.charAt(azar.nextInt(CodigoReserva.ALFABETO.length())));
                }
                codigo = sb.toString();
            } while (!codigos.add(codigo));
            return codigo;
        }
    }
}

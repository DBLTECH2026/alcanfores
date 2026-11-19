package pe.alcanfores.notificaciones.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.eventos.AsistenciaRegistradaEvento;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.AvisoServicio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Solo con {@code NOTIF_DATOS_DEMO=true} y la bandeja vacía: pasa por el flujo real unos eventos de clientes ficticios
 * (correos @example.com) para que el panel muestre avisos de todos los tipos y un recordatorio por vencer en ~3 min.
 */
@Component
@ConditionalOnProperty(prefix = "alcanfores.notificaciones", name = "datos-demo", havingValue = "true")
public class DatosDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);

    private final AvisoServicio avisos;
    private final NotificacionRepositorio notificaciones;

    public DatosDemo(AvisoServicio avisos, NotificacionRepositorio notificaciones) {
        this.avisos = avisos;
        this.notificaciones = notificaciones;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (notificaciones.count() > 0) {
            return;
        }
        LocalDateTime ahora = Tiempo.ahora().truncatedTo(ChronoUnit.MINUTES);

        ClienteContacto diego = new ClienteContacto("70123321", "Diego", "Ramos Quispe", "987654321", "diego.ramos@example.com");
        LocalDateTime inicio = ahora.plusHours(2).plusMinutes(3);
        DatosReserva r1 = new DatosReserva(1L, "ALC-7K3M9P", 1L, "Cancha 1 · Fútbol 5", "FUTBOL_5", inicio.toLocalDate(),
                inicio.toLocalTime(), inicio.toLocalTime().plusHours(1), dinero("80.00"), dinero("40.00"), BigDecimal.ZERO, diego);
        avisos.alRegistrarReserva(new ReservaRegistradaEvento(UUID.randomUUID(), ahora, r1, "WEB", "YAPE", ahora.plusMinutes(20)));
        DatosReserva r1Pagada = conPagado(r1, dinero("40.00"));
        avisos.alConfirmarReserva(new ReservaConfirmadaEvento(UUID.randomUUID(), ahora, r1Pagada, dinero("40.00")));
        avisos.alEmitirComprobante(new ComprobanteEmitidoEvento(UUID.randomUUID(), ahora, 1L, r1.codigoReserva(), "BOLETA",
                "B001-00000001", dinero("40.00"), diego));

        ClienteContacto maria = new ClienteContacto("45678912", "María Fernanda", "Torres Huamán", "912345678",
                "maria.torres@example.com");
        DatosReserva r2 = new DatosReserva(2L, "ALC-Q8W4ZR", 4L, "Losa multiusos", "MULTIUSO", ahora.toLocalDate().minusDays(1),
                LocalTime.of(20, 0), LocalTime.of(21, 0), dinero("50.00"), dinero("25.00"), dinero("50.00"), maria);
        avisos.alRegistrarAsistencia(new AsistenciaRegistradaEvento(UUID.randomUUID(), ahora, r2, "ASISTIO"));

        ClienteContacto jorge = new ClienteContacto("41234567", "Jorge Luis", "Paredes Soto", "956789123",
                "jorge.paredes@example.com");
        DatosReserva r3 = new DatosReserva(3L, "ALC-H5N2TB", 3L, "Cancha 3 · Fútbol 7", "FUTBOL_7", ahora.toLocalDate().plusDays(1),
                LocalTime.of(19, 0), LocalTime.of(20, 0), dinero("120.00"), dinero("60.00"), BigDecimal.ZERO, jorge);
        avisos.alRegistrarReserva(new ReservaRegistradaEvento(UUID.randomUUID(), ahora, r3, "WEB", "PLIN", ahora.plusMinutes(20)));
        avisos.alCancelarReserva(new ReservaCanceladaEvento(UUID.randomUUID(), ahora, r3, "SISTEMA",
                "Venció el plazo para pagar", false, BigDecimal.ZERO));

        log.info("Datos demo cargados: {} avisos en la bandeja", notificaciones.count());
    }

    private static DatosReserva conPagado(DatosReserva r, BigDecimal pagado) {
        return new DatosReserva(r.idReserva(), r.codigoReserva(), r.idCancha(), r.nombreCancha(), r.deporte(), r.fecha(),
                r.horaInicio(), r.horaFin(), r.montoTotal(), r.montoAdelanto(), pagado, r.cliente());
    }

    private static BigDecimal dinero(String v) {
        return new BigDecimal(v);
    }
}

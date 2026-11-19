package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.modelo.CanceladoPor;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.servicio.ExpiracionServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpiracionServicioImpl implements ExpiracionServicio {

    private static final Logger log = LoggerFactory.getLogger(ExpiracionServicioImpl.class);
    private static final List<EstadoPago> EXPIRABLES = List.of(EstadoPago.SIN_PAGO, EstadoPago.OBSERVADO);

    private final ReservaRepositorio reservas;
    private final OperacionesReserva operaciones;
    private final TransactionTemplate transacciones;

    @Override
    public int expirarVencidas() {
        LocalDateTime ahora = Tiempo.ahora();
        int expiradas = 0;
        for (Long id : reservas.idsVencidas(EstadoReserva.PENDIENTE, EXPIRABLES, ahora)) {
            try {
                Boolean hecha = transacciones.execute(estado -> expirar(id, ahora));
                if (Boolean.TRUE.equals(hecha)) {
                    expiradas++;
                }
            } catch (OptimisticLockingFailureException e) {
                log.info("La reserva {} cambió mientras se expiraba (llegó un pago); se omite", id);
            }
        }
        if (expiradas > 0) {
            log.info("Reservas expiradas por falta de pago: {}", expiradas);
        }
        return expiradas;
    }

    /** Cada reserva en su propia transacción: si una falla, las demás igual se expiran. */
    private boolean expirar(Long id, LocalDateTime ahora) {
        Reserva r = reservas.findById(id).orElse(null);
        if (r == null || r.getEstado() != EstadoReserva.PENDIENTE || !EXPIRABLES.contains(r.getEstadoPago())
                || r.getVenceEn() == null || !r.getVenceEn().isBefore(ahora)) {
            return false;
        }
        operaciones.cancelar(r, CanceladoPor.SISTEMA, null, ReglasReserva.MOTIVO_VENCIDA, false);
        return true;
    }
}

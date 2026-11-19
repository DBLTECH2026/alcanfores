package pe.alcanfores.reservas.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.reservas.dto.AdminDtos.AsistenciaSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.CancelacionAdminSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.ListaReservasRespuesta;
import pe.alcanfores.reservas.dto.AdminDtos.ReprogramacionAdminSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaAdminDetalle;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaManualRespuesta;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaManualSolicitud;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Origen;

import java.time.LocalDate;

/** Reservas desde el panel (ADMIN y RECEPCION). */
public interface ReservaAdminServicio {

    ListaReservasRespuesta listar(Filtro filtro, Pageable pagina);

    ReservaAdminDetalle detalle(String codigo);

    ReservaManualRespuesta registrarManual(ReservaManualSolicitud solicitud);

    ReservaAdminDetalle cancelar(String codigo, CancelacionAdminSolicitud solicitud);

    ReservaAdminDetalle registrarAsistencia(String codigo, AsistenciaSolicitud solicitud);

    /**
     * Cambia el horario conservando lo pagado. Sin el plazo ni el límite de un cambio del cliente; sí con las reglas de
     * estado y disponibilidad. Acepta el código («ALC-…») o el id numérico.
     */
    ReservaAdminDetalle reprogramar(String idOCodigo, ReprogramacionAdminSolicitud solicitud);

    byte[] constancia(String codigo);

    /** Filtros del listado; q busca por código, DNI, nombre o celular. */
    record Filtro(EstadoReserva estado, EstadoPago estadoPago, LocalDate fecha, LocalDate desde, LocalDate hasta,
                  Long idCancha, Origen origen, String q) {
    }
}

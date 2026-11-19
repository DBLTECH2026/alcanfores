package pe.alcanfores.reservas.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.reservas.dto.AdminDtos.ClienteDetalle;
import pe.alcanfores.reservas.dto.AdminDtos.ClienteResumen;
import pe.alcanfores.reservas.dto.PanelDtos.DashboardRespuesta;

import java.time.LocalDate;

/** Dashboard del día y clientes con métricas. */
public interface PanelServicio {

    DashboardRespuesta dashboard(LocalDate fecha);

    PaginaRespuesta<ClienteResumen> clientes(String q, Pageable pagina);

    ClienteDetalle cliente(String dni);
}

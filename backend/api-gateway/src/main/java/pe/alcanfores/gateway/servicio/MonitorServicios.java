package pe.alcanfores.gateway.servicio;

import pe.alcanfores.gateway.dto.EstadoSistemaRespuesta;
import reactor.core.publisher.Mono;

/** Consulta la salud de Eureka, del propio gateway y de cada microservicio esperado. */
public interface MonitorServicios {

    Mono<EstadoSistemaRespuesta> consultar();
}

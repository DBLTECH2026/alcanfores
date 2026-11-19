package pe.alcanfores.gateway.controlador;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.gateway.dto.EstadoSistemaRespuesta;
import pe.alcanfores.gateway.servicio.MonitorServicios;
import reactor.core.publisher.Mono;

/** Estado del sistema para la pantalla «Estado» del panel (público: no expone datos sensibles). */
@RestController
public class EstadoControlador {

    private final MonitorServicios monitor;

    public EstadoControlador(MonitorServicios monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/api/estado")
    public Mono<ResponseEntity<EstadoSistemaRespuesta>> estado() {
        return monitor.consultar().map(r -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r));
    }
}

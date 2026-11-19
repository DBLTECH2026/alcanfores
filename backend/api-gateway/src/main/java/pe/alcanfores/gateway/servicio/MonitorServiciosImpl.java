package pe.alcanfores.gateway.servicio;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.gateway.config.GatewayPropiedades;
import pe.alcanfores.gateway.dto.EstadoServicio;
import pe.alcanfores.gateway.dto.EstadoSistemaRespuesta;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Para cada servicio esperado busca sus instancias en Eureka ({@link ReactiveDiscoveryClient}) y llama a
 * {@code /actuator/health} de cada una (en paralelo, 2 s como máximo). Eureka no se registra a sí mismo: se consulta
 * por la URL de {@code eureka.client.service-url.defaultZone}. El propio gateway, si aún no figura en Eureka, se
 * consulta en su puerto local.
 */
@Service
public class MonitorServiciosImpl implements MonitorServicios {

    static final String EUREKA = "discovery-server";
    static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(2);

    private final ReactiveDiscoveryClient descubrimiento;
    private final WebClient cliente;
    private final GatewayPropiedades propiedades;
    private final Environment entorno;
    private final String propioNombre;

    public MonitorServiciosImpl(ReactiveDiscoveryClient descubrimiento, WebClient.Builder constructor,
                                GatewayPropiedades propiedades, Environment entorno,
                                @Value("${spring.application.name}") String propioNombre) {
        this.descubrimiento = descubrimiento;
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIEMPO_MAXIMO.toMillis())
                .responseTimeout(TIEMPO_MAXIMO);
        this.cliente = constructor.clone().clientConnector(new ReactorClientHttpConnector(http)).build();
        this.propiedades = propiedades;
        this.entorno = entorno;
        this.propioNombre = propioNombre;
    }

    @Override
    public Mono<EstadoSistemaRespuesta> consultar() {
        Flux<EstadoServicio> eureka = consultarEureka().flux();
        Flux<EstadoServicio> servicios = Flux.fromIterable(propiedades.servicios()).flatMapSequential(this::consultarServicio);
        return Flux.concat(eureka, servicios).collectList()
                .map(lista -> new EstadoSistemaRespuesta(Tiempo.ahora(), lista));
    }

    Mono<EstadoServicio> consultarEureka() {
        String zonas = entorno.getProperty("eureka.client.service-url.defaultZone", "http://localhost:8761/eureka");
        List<String> urls = Arrays.stream(zonas.split(","))
                .map(String::trim)
                .filter(z -> !z.isEmpty())
                .map(z -> z.replaceAll("/eureka/?$", "").replaceAll("/$", "") + "/actuator/health")
                .toList();
        return resumir(EUREKA, urls);
    }

    Mono<EstadoServicio> consultarServicio(String nombre) {
        return descubrimiento.getInstances(nombre)
                .collectList()
                .timeout(TIEMPO_MAXIMO)
                .onErrorReturn(List.of())
                .flatMap(instancias -> {
                    if (instancias.isEmpty() && nombre.equals(propioNombre)) {
                        String puerto = entorno.getProperty("local.server.port", entorno.getProperty("server.port", "8086"));
                        return resumir(nombre, List.of("http://localhost:" + puerto + "/actuator/health"));
                    }
                    if (instancias.isEmpty()) {
                        return Mono.just(EstadoServicio.caido(nombre, 0));
                    }
                    return resumir(nombre, instancias.stream().map(MonitorServiciosImpl::urlSalud).toList());
                });
    }

    private Mono<EstadoServicio> resumir(String nombre, List<String> urls) {
        return Flux.fromIterable(urls)
                .flatMap(this::sondear)
                .collectList()
                .map(sondas -> {
                    Long mejor = sondas.stream().filter(Sonda::arriba).map(Sonda::ms).min(Long::compare).orElse(null);
                    return new EstadoServicio(nombre, mejor == null ? EstadoServicio.DOWN : EstadoServicio.UP,
                            urls.size(), mejor);
                });
    }

    /** Llama a una URL de salud y mide cuánto tarda; cualquier error cuenta como caído. */
    Mono<Sonda> sondear(String url) {
        return Mono.defer(() -> {
            long inicio = System.nanoTime();
            return cliente.get().uri(url)
                    .exchangeToMono(r -> r.statusCode().is2xxSuccessful()
                            ? r.bodyToMono(Map.class).map(cuerpo -> EstadoServicio.UP.equals(cuerpo.get("status")))
                            : r.releaseBody().thenReturn(false))
                    .map(arriba -> new Sonda(arriba, (System.nanoTime() - inicio) / 1_000_000))
                    .timeout(TIEMPO_MAXIMO)
                    .onErrorReturn(new Sonda(false, 0));
        });
    }

    private static String urlSalud(ServiceInstance i) {
        String base = Objects.toString(i.getUri(), "").replaceAll("/$", "");
        return base + "/actuator/health";
    }

    record Sonda(boolean arriba, long ms) {
    }
}

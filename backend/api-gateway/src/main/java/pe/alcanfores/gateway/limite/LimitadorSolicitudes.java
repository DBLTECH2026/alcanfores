package pe.alcanfores.gateway.limite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import pe.alcanfores.gateway.config.GatewayPropiedades;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Limitador en memoria por IP y por regla (SPEC §1.1): login 10/min, documentos 20/min, registrar reserva 10/min,
 * pasarela 30/min; v2: registro de clientes 5/min y login de clientes 10/min. Cada combinación regla+IP tiene su propia {@link CubetaTokens}. Con varias réplicas del gateway
 * cada una lleva su cuenta (suficiente para este despliegue de una sola instancia).
 */
@Component
public class LimitadorSolicitudes {

    private static final Logger log = LoggerFactory.getLogger(LimitadorSolicitudes.class);
    private static final long INACTIVIDAD_NANOS = TimeUnit.MINUTES.toNanos(10);

    private final List<Regla> reglas;
    private final Map<String, CubetaTokens> cubetas = new ConcurrentHashMap<>();
    private final LongSupplier reloj;

    @Autowired
    public LimitadorSolicitudes(GatewayPropiedades propiedades) {
        this(propiedades, System::nanoTime);
    }

    LimitadorSolicitudes(GatewayPropiedades propiedades, LongSupplier reloj) {
        PathPatternParser parser = new PathPatternParser();
        this.reglas = propiedades.limites().stream()
                .filter(l -> l.porMinuto() > 0)
                .map(l -> new Regla(l.nombre(), l.metodo() == null || l.metodo().isBlank() ? null : HttpMethod.valueOf(l.metodo().trim()),
                        l.rutas().stream().map(parser::parse).toList(), l.porMinuto()))
                .toList();
        this.reloj = reloj;
    }

    /** Regla que aplica a la solicitud, si alguna. */
    public Optional<Regla> reglaPara(HttpMethod metodo, String ruta) {
        PathContainer camino = PathContainer.parsePath(ruta);
        return reglas.stream()
                .filter(r -> r.metodo() == null || r.metodo().equals(metodo))
                .filter(r -> r.patrones().stream().anyMatch(p -> p.matches(camino)))
                .findFirst();
    }

    public Resultado consumir(Regla regla, String ip) {
        long ahora = reloj.getAsLong();
        CubetaTokens cubeta = cubetas.computeIfAbsent(regla.nombre() + "|" + ip, k -> new CubetaTokens(regla.porMinuto(), ahora));
        CubetaTokens.Consumo c = cubeta.consumir(ahora);
        if (!c.permitido()) {
            log.warn("Límite «{}» alcanzado por la IP {} ({} por minuto)", regla.nombre(), ip, regla.porMinuto());
        }
        return new Resultado(c.permitido(), regla.porMinuto(), c.restantes(), c.esperaSegundos());
    }

    /** Libera memoria: descarta cubetas llenas que nadie usa hace 10 minutos. */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void limpiar() {
        long ahora = reloj.getAsLong();
        cubetas.entrySet().removeIf(e -> e.getValue().ociosa(ahora, INACTIVIDAD_NANOS));
    }

    int cubetasActivas() {
        return cubetas.size();
    }

    public record Regla(String nombre, HttpMethod metodo, List<PathPattern> patrones, int porMinuto) {
    }

    public record Resultado(boolean permitido, int limite, int restantes, long esperaSegundos) {
    }
}

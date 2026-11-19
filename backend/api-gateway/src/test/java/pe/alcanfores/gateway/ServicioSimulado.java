package pe.alcanfores.gateway;

import io.netty.handler.codec.http.HttpHeaderNames;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;

/**
 * Microservicio de mentira para las pruebas: responde la salud, una ruta lenta y, en cualquier otra ruta, devuelve
 * en JSON lo que recibió (método, ruta y cabeceras clave) para comprobar qué le llegó a través del gateway.
 */
public final class ServicioSimulado {

    private static DisposableServer servidor;

    private ServicioSimulado() {
    }

    public static synchronized int puerto() {
        if (servidor == null) {
            servidor = HttpServer.create().host("localhost").port(0)
                    .handle((req, res) -> {
                        String ruta = req.fullPath();
                        res.header(HttpHeaderNames.CONTENT_TYPE, "application/json");
                        if (ruta.equals("/actuator/health")) {
                            return res.sendString(Mono.just("{\"status\":\"UP\"}"));
                        }
                        Mono<String> eco = req.receive().aggregate().asString().defaultIfEmpty("")
                                .map(cuerpo -> "{\"metodo\":\"" + req.method().name() + "\",\"ruta\":\"" + ruta
                                        + "\",\"requestId\":\"" + valor(req.requestHeaders().get("X-Request-Id"))
                                        + "\",\"internoToken\":\"" + valor(req.requestHeaders().get("X-Interno-Token"))
                                        + "\",\"ip\":\"" + valor(req.requestHeaders().get("X-Forwarded-For"))
                                        + "\",\"bytes\":" + cuerpo.length() + "}");
                        if (ruta.equals("/api/auth/lento")) {
                            eco = eco.delayElement(Duration.ofSeconds(4));
                        }
                        return res.sendString(eco);
                    })
                    .bindNow();
        }
        return servidor.port();
    }

    private static String valor(String v) {
        return v == null ? "" : v;
    }
}

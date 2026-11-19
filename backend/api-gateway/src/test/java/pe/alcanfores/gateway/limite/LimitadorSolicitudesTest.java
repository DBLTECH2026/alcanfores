package pe.alcanfores.gateway.limite;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.util.unit.DataSize;
import pe.alcanfores.gateway.config.GatewayPropiedades;
import pe.alcanfores.gateway.config.GatewayPropiedades.Limite;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorSolicitudesTest {

    private final AtomicLong reloj = new AtomicLong(0);
    private final LimitadorSolicitudes limitador = new LimitadorSolicitudes(new GatewayPropiedades(DataSize.ofMegabytes(6),
            Duration.ofSeconds(10), 20_000, 20_000, 60_000, List.of(), List.of(
            new Limite("login", "POST", List.of("/api/auth/login"), 10),
            new Limite("documentos", null, List.of("/api/documentos/**"), 20),
            new Limite("reservas", "POST", List.of("/api/reservas"), 10),
            new Limite("pasarela", "", List.of("/api/pasarela/**"), 30))), reloj::get);

    @Test
    void eligeLaReglaPorMetodoYRuta() {
        assertThat(limitador.reglaPara(HttpMethod.POST, "/api/auth/login")).get().extracting(LimitadorSolicitudes.Regla::nombre).isEqualTo("login");
        assertThat(limitador.reglaPara(HttpMethod.GET, "/api/auth/login")).isEmpty();
        assertThat(limitador.reglaPara(HttpMethod.GET, "/api/documentos/dni/12345678")).get()
                .extracting(LimitadorSolicitudes.Regla::nombre).isEqualTo("documentos");
        assertThat(limitador.reglaPara(HttpMethod.POST, "/api/reservas")).isPresent();
        assertThat(limitador.reglaPara(HttpMethod.POST, "/api/reservas/ALC-ABCDEF/cancelar")).isEmpty();
        assertThat(limitador.reglaPara(HttpMethod.POST, "/api/pasarela/intentos/ALP-1/tarjeta")).get()
                .extracting(LimitadorSolicitudes.Regla::porMinuto).isEqualTo(30);
    }

    @Test
    void permiteElCupoYLuegoRecargaUnaFichaCadaSeisSegundos() {
        LimitadorSolicitudes.Regla login = limitador.reglaPara(HttpMethod.POST, "/api/auth/login").orElseThrow();
        for (int i = 0; i < 10; i++) {
            LimitadorSolicitudes.Resultado r = limitador.consumir(login, "1.1.1.1");
            assertThat(r.permitido()).isTrue();
            assertThat(r.restantes()).isEqualTo(9 - i);
        }
        LimitadorSolicitudes.Resultado bloqueado = limitador.consumir(login, "1.1.1.1");
        assertThat(bloqueado.permitido()).isFalse();
        assertThat(bloqueado.esperaSegundos()).isEqualTo(6);

        reloj.addAndGet(TimeUnit.SECONDS.toNanos(6));
        assertThat(limitador.consumir(login, "1.1.1.1").permitido()).isTrue();
        assertThat(limitador.consumir(login, "1.1.1.1").permitido()).isFalse();
    }

    @Test
    void cadaIpTieneSuPropiaCubeta() {
        LimitadorSolicitudes.Regla login = limitador.reglaPara(HttpMethod.POST, "/api/auth/login").orElseThrow();
        for (int i = 0; i < 10; i++) {
            limitador.consumir(login, "2.2.2.2");
        }
        assertThat(limitador.consumir(login, "2.2.2.2").permitido()).isFalse();
        assertThat(limitador.consumir(login, "3.3.3.3").permitido()).isTrue();
    }

    @Test
    void laLimpiezaDescartaCubetasLlenasYSinUso() {
        LimitadorSolicitudes.Regla login = limitador.reglaPara(HttpMethod.POST, "/api/auth/login").orElseThrow();
        limitador.consumir(login, "4.4.4.4");
        limitador.limpiar();
        assertThat(limitador.cubetasActivas()).isEqualTo(1);
        reloj.addAndGet(TimeUnit.MINUTES.toNanos(11));
        limitador.limpiar();
        assertThat(limitador.cubetasActivas()).isZero();
    }
}

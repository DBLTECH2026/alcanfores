package pe.alcanfores.pagos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.pagos.servicio.NumeracionComprobantes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Numeración bajo bloqueo pesimista en MariaDB real: muchas emisiones simultáneas sin huecos ni duplicados. */
class NumeracionConcurrenteTest extends IntegracionBase {

    @Autowired
    NumeracionComprobantes numeracion;
    @Autowired
    PlatformTransactionManager transacciones;

    @Test
    void cienCorrelativosSimultaneosSinHuecosNiDuplicados() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futuros.add(hilos.submit(() -> tx.execute(e -> numeracion.siguiente("B001"))));
            }
            List<Integer> numeros = new ArrayList<>();
            for (Future<Integer> f : futuros) {
                numeros.add(f.get());
            }
            Collections.sort(numeros);
            assertThat(numeros).containsExactlyElementsOf(IntStream.rangeClosed(1, 100).boxed().toList());
            assertThat(jdbc.queryForObject("SELECT ultimo_correlativo FROM serie WHERE serie = 'B001'", Integer.class)).isEqualTo(100);
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void unaTransaccionRevertidaNoConsumeNumero() {
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        tx.executeWithoutResult(e -> {
            numeracion.siguiente("F001");
            e.setRollbackOnly();
        });
        Integer siguiente = tx.execute(e -> numeracion.siguiente("F001"));
        assertThat(siguiente).isEqualTo(1);
    }

    @Test
    void cobrosSimultaneosEnCajaEmitenBoletasCorrelativas() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> cobros = new ArrayList<>();
            String alfabeto = "ABCDEFGHJKLM";
            for (int i = 0; i < 12; i++) {
                String codigo = "ALC-CAJA" + alfabeto.charAt(i) + "Z";
                String cuerpo = """
                        {"codigoReserva":"%s","tipoPago":"TOTAL","metodo":"EFECTIVO","monto":90.00,
                         "reserva":{"codigoReserva":"%s","nombreCancha":"Cancha 2","deporte":"FUTBOL_6","montoTotal":90.00,
                          "montoAdelanto":45.00,"montoPagado":0,"cliente":{"dni":"%s","nombres":"Ana","apellidos":"Pérez"}}}
                        """.formatted(codigo, codigo, DNI);
                cobros.add(() -> mvc.perform(post("/interno/pagos/caja")
                                .header("X-Interno-Token", "token-interno-solo-para-desarrollo-local")
                                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                        .andReturn().getResponse().getStatus());
            }
            for (Future<Integer> f : hilos.invokeAll(cobros)) {
                assertThat(f.get()).isEqualTo(201);
            }
        } finally {
            hilos.shutdownNow();
        }
        List<Integer> correlativos = jdbc.queryForList("SELECT correlativo FROM comprobante WHERE serie = 'B001' ORDER BY correlativo", Integer.class);
        assertThat(correlativos).containsExactlyElementsOf(IntStream.rangeClosed(1, 12).boxed().toList());
    }
}

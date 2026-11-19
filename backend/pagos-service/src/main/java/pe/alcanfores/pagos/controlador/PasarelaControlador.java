package pe.alcanfores.pagos.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.pagos.dto.CheckoutRespuesta;
import pe.alcanfores.pagos.pasarela.simulador.ResultadoPasarela;
import pe.alcanfores.pagos.pasarela.simulador.SimuladorPasarela;
import pe.alcanfores.pagos.pasarela.simulador.Solicitud3ds;
import pe.alcanfores.pagos.pasarela.simulador.SolicitudTarjeta;
import pe.alcanfores.pagos.pasarela.simulador.SolicitudYape;
import pe.alcanfores.pagos.servicio.IntentoPagoServicio;

/** API del proveedor simulado «Alcanfores Pay (beta)» que usa la página de checkout {@code /pasarela/ALP-…}. */
@Tag(name = "Alcanfores Pay (beta)", description = "Checkout de la pasarela simulada: tarjeta, 3-D Secure y Yape")
@RestController
@RequestMapping("/api/pasarela/intentos/{referencia}")
public class PasarelaControlador {

    private final IntentoPagoServicio intentos;
    private final SimuladorPasarela pasarela;

    public PasarelaControlador(IntentoPagoServicio intentos, SimuladorPasarela pasarela) {
        this.intentos = intentos;
        this.pasarela = pasarela;
    }

    @Operation(summary = "Datos del checkout", description = "Comercio, concepto, monto, estado y vencimiento del intento.")
    @GetMapping
    public CheckoutRespuesta checkout(@PathVariable String referencia) {
        return intentos.checkout(referencia);
    }

    @Operation(summary = "Pagar con tarjeta", description = "Valida Luhn, marca, vencimiento y CVV; decide con las tarjetas de prueba. "
            + "Nunca guarda el número completo ni el CVV.")
    @PostMapping("/tarjeta")
    public ResultadoPasarela tarjeta(@PathVariable String referencia, @RequestBody SolicitudTarjeta tarjeta) {
        return pasarela.pagarConTarjeta(referencia, tarjeta);
    }

    @Operation(summary = "Confirmar 3-D Secure", description = "En la beta el código 123456 aprueba; cualquier otro rechaza.")
    @PostMapping("/3ds")
    public ResultadoPasarela tresDs(@PathVariable String referencia, @RequestBody Solicitud3ds solicitud) {
        return pasarela.confirmar3ds(referencia, solicitud);
    }

    @Operation(summary = "Pagar con Yape (beta)", description = "Celular válido + código 123456 aprueba; 000000 rechaza por saldo insuficiente.")
    @PostMapping("/yape")
    public ResultadoPasarela yape(@PathVariable String referencia, @RequestBody SolicitudYape solicitud) {
        return pasarela.pagarConYape(referencia, solicitud);
    }
}

package pe.alcanfores.pagos.pasarela.simulador;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.modelo.IntentoPago;
import pe.alcanfores.pagos.servicio.IntentoPagoServicio;
import pe.alcanfores.pagos.servicio.ValidadorSolicitudes;

import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Alcanfores Pay (beta)»: se comporta como un PROVEEDOR EXTERNO. Recibe la tarjeta o el Yape del checkout, decide con
 * las tarjetas de prueba, guarda su propia transacción (sin número completo ni CVV) y avisa al comercio por webhook
 * firmado. El comercio aprueba el pago al recibir el aviso, no por esta respuesta.
 * <p>No es transaccional a propósito: el aviso HTTP llega al comercio sin bloqueos abiertos en este hilo.</p>
 */
@Service
public class SimuladorPasarela {

    private static final Logger log = LoggerFactory.getLogger(SimuladorPasarela.class);
    private static final SecureRandom AZAR = new SecureRandom();

    private final IntentoPagoServicio intentos;
    private final TransaccionPasarelaRepositorio transacciones;
    private final NotificadorComercio notificador;
    private final ValidadorSolicitudes validador;
    private final ObjectMapper json;

    public SimuladorPasarela(IntentoPagoServicio intentos, TransaccionPasarelaRepositorio transacciones,
                             NotificadorComercio notificador, ValidadorSolicitudes validador, ObjectMapper json) {
        this.intentos = intentos;
        this.transacciones = transacciones;
        this.notificador = notificador;
        this.validador = validador;
        this.json = json;
    }

    public ResultadoPasarela pagarConTarjeta(String referencia, SolicitudTarjeta tarjeta) {
        IntentoPago intento = intentos.abiertoParaCobro(referencia);
        List<ErrorCampo> errores = ValidadorTarjeta.validar(tarjeta, YearMonth.from(Tiempo.hoy()));
        ValidadorSolicitudes.lanzarSiHay(errores);

        String numero = tarjeta.numeroLimpio();
        MarcaTarjeta marca = MarcaTarjeta.detectar(numero).orElseThrow();
        // Solo marca y últimos 4: el número completo y el CVV nunca se guardan ni se escriben en logs.
        log.info("Alcanfores Pay beta · cobro con {} {} · referencia {}", marca.nombre(), tarjeta.enmascarado(), referencia);
        TarjetasPrueba.Resultado resultado = TarjetasPrueba.resultado(numero);

        TransaccionPasarela t = nueva(intento, TransaccionPasarela.Metodo.TARJETA, marca.nombre(), tarjeta.ultimos4());
        if (resultado == TarjetasPrueba.Resultado.REQUIERE_3DS) {
            t.setEstado(TransaccionPasarela.Estado.PENDIENTE_3DS);
            transacciones.save(t);
            intentos.marcarRequiere3ds(referencia);
            return new ResultadoPasarela(ResultadoPasarela.REQUIERE_3DS, null, null, marca.nombre(), tarjeta.ultimos4());
        }
        if (resultado == TarjetasPrueba.Resultado.APROBADA) {
            return aprobar(t);
        }
        return rechazar(t, resultado.motivo());
    }

    public ResultadoPasarela confirmar3ds(String referencia, Solicitud3ds solicitud) {
        ValidadorSolicitudes.lanzarSiHay(validador.errores(solicitud));
        intentos.abiertoParaCobro(referencia);
        TransaccionPasarela t = transacciones
                .findFirstByReferenciaAndEstadoOrderByIdDesc(referencia, TransaccionPasarela.Estado.PENDIENTE_3DS)
                .orElseThrow(() -> NegocioExcepcion.conflicto("SIN_3DS_PENDIENTE",
                        "No hay una verificación 3-D Secure pendiente para este pago."));
        if (TarjetasPrueba.CODIGO_3DS.equals(solicitud.codigo())) {
            return aprobar(t);
        }
        return rechazar(t, "La verificación 3-D Secure no fue exitosa.");
    }

    public ResultadoPasarela pagarConYape(String referencia, SolicitudYape solicitud) {
        ValidadorSolicitudes.lanzarSiHay(validador.errores(solicitud));
        IntentoPago intento = intentos.abiertoParaCobro(referencia);
        String celular = Texto.normalizarCelular(solicitud.celular());
        TransaccionPasarela t = nueva(intento, TransaccionPasarela.Metodo.YAPE, "Yape", celular.substring(celular.length() - 4));
        log.info("Alcanfores Pay beta · cobro con Yape *****{} · referencia {}", t.getUltimos4(), referencia);
        return switch (solicitud.codigoAprobacion()) {
            case TarjetasPrueba.YAPE_APRUEBA -> aprobar(t);
            case TarjetasPrueba.YAPE_RECHAZA -> rechazar(t, "Saldo insuficiente en Yape.");
            default -> rechazar(t, "El código de aprobación de Yape no es correcto.");
        };
    }

    private TransaccionPasarela nueva(IntentoPago intento, TransaccionPasarela.Metodo metodo, String marca, String ultimos4) {
        TransaccionPasarela t = new TransaccionPasarela();
        t.setIdTransaccion(UUID.randomUUID().toString());
        t.setReferencia(intento.getReferencia());
        t.setMetodo(metodo);
        t.setMarca(marca);
        t.setUltimos4(ultimos4);
        t.setMonto(intento.getMonto());
        t.setCreadoEn(Tiempo.ahora());
        return t;
    }

    private ResultadoPasarela aprobar(TransaccionPasarela t) {
        t.setEstado(TransaccionPasarela.Estado.APROBADA);
        t.setCodigoAutorizacion(String.format("%06d", AZAR.nextInt(1_000_000)));
        avisar(t, "pago.aprobado");
        return new ResultadoPasarela(ResultadoPasarela.APROBADO, t.getCodigoAutorizacion(), null, t.getMarca(), t.getUltimos4());
    }

    private ResultadoPasarela rechazar(TransaccionPasarela t, String motivo) {
        t.setEstado(TransaccionPasarela.Estado.RECHAZADA);
        t.setMotivo(motivo);
        avisar(t, "pago.rechazado");
        return new ResultadoPasarela(ResultadoPasarela.RECHAZADO, null, motivo, t.getMarca(), t.getUltimos4());
    }

    /** Arma el aviso (cuerpo fijo, mismo id en cada reintento), lo guarda y lo entrega al comercio. */
    private void avisar(TransaccionPasarela t, String tipo) {
        Map<String, Object> aviso = new LinkedHashMap<>();
        aviso.put("idEvento", UUID.randomUUID().toString());
        aviso.put("tipo", tipo);
        aviso.put("referencia", t.getReferencia());
        aviso.put("idTransaccion", t.getIdTransaccion());
        aviso.put("metodo", t.getMetodo().name());
        aviso.put("marca", t.getMarca());
        aviso.put("ultimos4", t.getUltimos4());
        aviso.put("codigoAutorizacion", t.getCodigoAutorizacion());
        aviso.put("monto", t.getMonto());
        aviso.put("moneda", "PEN");
        aviso.put("motivo", t.getMotivo());
        aviso.put("ocurridoEn", Tiempo.ahora().toString());
        try {
            t.setCuerpoNotificacion(json.writeValueAsString(aviso));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo armar el aviso de la pasarela", e);
        }
        t.setIdEvento((String) aviso.get("idEvento"));
        t.setNotificada(false);
        t.setProximoIntento(Tiempo.ahora());
        transacciones.save(t);
        notificador.entregar(t);
    }
}

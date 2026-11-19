package pe.alcanfores.pagos.servicio;

import pe.alcanfores.pagos.dto.CheckoutRespuesta;
import pe.alcanfores.pagos.dto.EstadoIntentoRespuesta;
import pe.alcanfores.pagos.dto.IntentoCreadoRespuesta;
import pe.alcanfores.pagos.dto.PreferenciaRespuesta;
import pe.alcanfores.pagos.dto.SolicitudIntento;
import pe.alcanfores.pagos.modelo.IntentoPago;

/** Pagos en línea: intentos de la pasarela beta y preferencias de Mercado Pago. */
public interface IntentoPagoServicio {

    IntentoCreadoRespuesta crearIntentoBeta(SolicitudIntento solicitud);

    /** 409 {@code MP_NO_CONFIGURADO} si no hay {@code MP_ACCESS_TOKEN}. */
    PreferenciaRespuesta crearPreferenciaMercadoPago(SolicitudIntento solicitud);

    /** Datos de la sesión para la página de checkout de la pasarela beta. */
    CheckoutRespuesta checkout(String referencia);

    /** Sondeo del resultado; si es de Mercado Pago y sigue pendiente, consulta a Mercado Pago por la referencia. */
    EstadoIntentoRespuesta estado(String referencia);

    /** El proveedor beta pide autorizar un cobro: el intento debe existir, estar abierto y no haber expirado. */
    IntentoPago abiertoParaCobro(String referencia);

    void marcarRequiere3ds(String referencia);

    /** Tarea programada: intentos sin pagar pasados sus 15 minutos → EXPIRADO. */
    int expirarVencidos();
}

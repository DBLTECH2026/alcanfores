package pe.alcanfores.notificaciones.servicio.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.canal.ArchivoPdf;
import pe.alcanfores.notificaciones.canal.CanalEnvio;
import pe.alcanfores.notificaciones.canal.EnvioExcepcion;
import pe.alcanfores.notificaciones.cliente.PagosCliente;
import pe.alcanfores.notificaciones.cliente.ReservasCliente;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envío de avisos. Nunca lanza excepción por una falla del proveedor: la deja anotada en el aviso (FALLIDO,
 * {@code intentos + 1}, {@code ultimo_error}) para que la tarea de reintentos lo vuelva a intentar.
 * Si un PDF no se pudo descargar, el mensaje sale igual sin él y el adjunto queda NO_DISPONIBLE con el motivo.
 */
@Service
public class EnvioServicioImpl implements EnvioServicio {

    private static final Logger log = LoggerFactory.getLogger(EnvioServicioImpl.class);
    private static final byte[] FIRMA_PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final Map<CanalNotificacion, CanalEnvio> canales = new EnumMap<>(CanalNotificacion.class);
    private final ReservasCliente reservas;
    private final PagosCliente pagos;
    private final NotificacionRepositorio notificaciones;

    public EnvioServicioImpl(List<CanalEnvio> canales, ReservasCliente reservas, PagosCliente pagos,
                             NotificacionRepositorio notificaciones) {
        canales.forEach(c -> this.canales.put(c.canal(), c));
        this.reservas = reservas;
        this.pagos = pagos;
        this.notificaciones = notificaciones;
    }

    @Override
    public List<Notificacion> enviarTodas(List<Notificacion> lista) {
        Map<String, Descarga> descargas = new HashMap<>();
        List<Notificacion> resultado = new ArrayList<>(lista.size());
        for (Notificacion n : lista) {
            resultado.add(enviar(n, descargas));
        }
        return resultado;
    }

    @Override
    public Notificacion enviar(Notificacion notificacion) {
        return enviar(notificacion, new HashMap<>());
    }

    private Notificacion enviar(Notificacion n, Map<String, Descarga> descargas) {
        List<ArchivoPdf> pdfs = new ArrayList<>();
        List<Adjunto> adjuntos = new ArrayList<>();
        for (Adjunto a : n.getAdjuntos()) {
            Descarga d = descargas.computeIfAbsent(a.tipo() + ":" + a.referencia(), k -> descargar(a));
            if (d.contenido() != null) {
                pdfs.add(new ArchivoPdf(a.nombreArchivo(), d.contenido()));
                adjuntos.add(a.adjuntado());
            } else {
                adjuntos.add(a.noDisponible(d.motivo()));
            }
        }
        n.actualizarAdjuntos(adjuntos);

        CanalEnvio canal = canales.get(n.getCanal());
        if (canal == null || !canal.configurado()) {
            n.registrarResultado(EstadoNotificacion.SIMULADO, null, Tiempo.ahora());
        } else {
            try {
                canal.enviar(n, pdfs);
                n.registrarResultado(EstadoNotificacion.ENVIADO, null, Tiempo.ahora());
            } catch (EnvioExcepcion e) {
                log.warn("Aviso {} por {} a {} falló (intento {}): {}", n.getEvento(), n.getCanal(), n.getDestino(),
                        n.getIntentos() + 1, e.getMessage());
                n.registrarResultado(EstadoNotificacion.FALLIDO, e.getMessage(), Tiempo.ahora());
            }
        }
        return notificaciones.save(n);
    }

    private Descarga descargar(Adjunto a) {
        try {
            byte[] pdf = switch (a.tipo()) {
                case CONSTANCIA -> reservas.constanciaPdf(a.referencia());
                case COMPROBANTE -> pagos.comprobantePdf(Long.valueOf(a.referencia()));
            };
            if (!esPdf(pdf)) {
                return new Descarga(null, "El archivo recibido no es un PDF válido.");
            }
            return new Descarga(pdf, null);
        } catch (NegocioExcepcion e) {
            log.warn("No se pudo descargar {} {}: {}", a.tipo(), a.referencia(), e.getMessage());
            return new Descarga(null, "No se pudo descargar el PDF: " + e.getMessage());
        }
    }

    static boolean esPdf(byte[] contenido) {
        if (contenido == null || contenido.length < FIRMA_PDF.length) {
            return false;
        }
        for (int i = 0; i < FIRMA_PDF.length; i++) {
            if (contenido[i] != FIRMA_PDF[i]) {
                return false;
            }
        }
        return true;
    }

    private record Descarga(byte[] contenido, String motivo) {
    }
}

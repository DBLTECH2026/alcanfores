package pe.alcanfores.notificaciones.servicio.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.cliente.CatalogoCliente;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.servicio.ComplejoServicio;
import pe.alcanfores.notificaciones.servicio.DatosComplejo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Lee {@code GET /interno/configuracion} de catalogo-service y lo guarda 5 minutos (cambios del admin se ven pronto
 * sin consultar en cada mensaje). Si catálogo no responde, usa los datos reales del complejo de application.yml
 * y vuelve a intentar en 1 minuto.
 */
@Service
public class ComplejoServicioImpl implements ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicioImpl.class);
    static final Duration VIGENCIA = Duration.ofMinutes(5);
    static final Duration VIGENCIA_RESPALDO = Duration.ofMinutes(1);
    static final int DEVOLUCION_HORAS_POR_DEFECTO = 24;

    private final CatalogoCliente catalogo;
    private final NotificacionesPropiedades propiedades;
    private volatile DatosComplejo enCache;
    private volatile LocalDateTime venceCache = LocalDateTime.MIN;

    public ComplejoServicioImpl(CatalogoCliente catalogo, NotificacionesPropiedades propiedades) {
        this.catalogo = catalogo;
        this.propiedades = propiedades;
    }

    @Override
    public DatosComplejo datos() {
        LocalDateTime ahora = Tiempo.ahora();
        if (enCache != null && ahora.isBefore(venceCache)) {
            return enCache;
        }
        DatosComplejo nuevos;
        Duration vigencia;
        try {
            nuevos = combinar(catalogo.configuracion());
            vigencia = VIGENCIA;
        } catch (NegocioExcepcion e) {
            log.warn("Configuración del complejo no disponible ({}); se usan los datos por defecto", e.getMessage());
            nuevos = combinar(Map.of());
            vigencia = VIGENCIA_RESPALDO;
        }
        enCache = nuevos;
        venceCache = ahora.plus(vigencia);
        return nuevos;
    }

    DatosComplejo combinar(Map<String, String> c) {
        NotificacionesPropiedades.Complejo d = propiedades.getComplejo();
        return new DatosComplejo(
                texto(c, "nombre_complejo", d.getNombre()),
                texto(c, "direccion", d.getDireccion()),
                texto(c, "referencia", d.getReferencia()),
                texto(c, "mapa_url", d.getMapaUrl()),
                texto(c, "whatsapp", d.getWhatsapp()),
                texto(c, "correo", d.getCorreo()),
                entero(c, "recordatorio_horas", propiedades.getRecordatorio().getHorasPorDefecto(), 1, 48),
                entero(c, "devolucion_horas", DEVOLUCION_HORAS_POR_DEFECTO, 0, 720));
    }

    private static String texto(Map<String, String> c, String clave, String porDefecto) {
        String v = c.get(clave);
        return v == null || v.isBlank() ? porDefecto : v.trim();
    }

    private static int entero(Map<String, String> c, String clave, int porDefecto, int min, int max) {
        try {
            int v = Integer.parseInt(c.getOrDefault(clave, "").trim());
            return v < min || v > max ? porDefecto : v;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}

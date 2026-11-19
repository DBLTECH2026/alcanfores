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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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

    @Override
    public DatosComplejo actualizados() {
        try {
            DatosComplejo nuevos = combinar(catalogo.configuracion());
            enCache = nuevos;
            venceCache = Tiempo.ahora().plus(VIGENCIA);
            return nuevos;
        } catch (NegocioExcepcion e) {
            log.warn("Configuración del complejo no disponible al confirmar ({}); se usa la última conocida", e.getMessage());
            return datos();
        }
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
                minutosRecordatorio(c.get("recordatorio_minutos"), propiedades.getRecordatorio().getMinutosPorDefecto()),
                entero(c, "devolucion_horas", DEVOLUCION_HORAS_POR_DEFECTO, 0, 720));
    }

    /**
     * «120,15» → [120, 15] (de mayor a menor). Si el valor no cumple la regla del catálogo (1 a 3 valores de 5 a 1440,
     * sin repetir) se usan los minutos por defecto: nunca se deja una reserva sin recordatorio.
     */
    static List<Integer> minutosRecordatorio(String valor, List<Integer> porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            List<Integer> lista = Arrays.stream(valor.split(",")).map(String::trim).map(Integer::valueOf).toList();
            boolean valida = !lista.isEmpty() && lista.size() <= 3 && lista.stream().distinct().count() == lista.size()
                    && lista.stream().allMatch(m -> m >= 5 && m <= 1440);
            return valida ? lista.stream().sorted(Comparator.reverseOrder()).toList() : porDefecto;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
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

package pe.alcanfores.comun.auditoria;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PublicadorEventos;
import pe.alcanfores.comun.seguridad.SeguridadUtil;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;

import java.util.UUID;

/**
 * Registra en la bitácora central (auth-service) quién hizo qué, cuándo y desde dónde (RNF-19).
 * Ejemplo: {@code auditor.registrar("VALIDAR_PAGO", "pago", pago.getId(), "Reserva ALC-7K3M9P · S/ 40.00");}
 */
public class Auditor {

    private final PublicadorEventos publicador;
    private final String servicio;

    public Auditor(PublicadorEventos publicador, String servicio) {
        this.publicador = publicador;
        this.servicio = servicio;
    }

    public void registrar(String accion, String entidad, Object idEntidad, String detalle) {
        UsuarioToken u = SeguridadUtil.actual().orElse(null);
        String det = detalle != null && detalle.length() > 500 ? detalle.substring(0, 497) + "..." : detalle;
        publicador.publicar(Eventos.AUDITORIA_ACCION, new AccionAuditadaEvento(UUID.randomUUID(), Tiempo.ahora(), servicio,
                u == null ? null : u.id(), u == null ? "Sistema" : u.usuario(), u == null ? "SISTEMA" : u.rol(),
                accion, entidad, idEntidad == null ? null : String.valueOf(idEntidad), det, ipCliente()));
    }

    /** Registro hecho por un cliente (sin sesión) o por el propio sistema: se indica el actor a mano. */
    public void registrarComo(String actor, String rol, String accion, String entidad, Object idEntidad, String detalle) {
        publicador.publicar(Eventos.AUDITORIA_ACCION, new AccionAuditadaEvento(UUID.randomUUID(), Tiempo.ahora(), servicio,
                null, actor, rol, accion, entidad, idEntidad == null ? null : String.valueOf(idEntidad), detalle, ipCliente()));
    }

    static String ipCliente() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atr)) {
            return null;
        }
        HttpServletRequest req = atr.getRequest();
        String reenviada = req.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}

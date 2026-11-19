package pe.alcanfores.auth.servicio;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** IP de quien hizo la solicitud: la primera de {@code X-Forwarded-For} (la pone el gateway) o la remota. */
final class IpCliente {

    private IpCliente() {
    }

    static String actual() {
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

package pe.alcanfores.comun.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import pe.alcanfores.comun.error.ErrorApi;

import java.io.IOException;

/** Respuestas 401 y 403 con el cuerpo de error estándar (RNF-06). */
public class ManejadoresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;

    public ManejadoresSeguridad(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException e) throws IOException {
        Object detalle = req.getAttribute(JwtFiltroAutenticacion.ATRIBUTO_ERROR);
        String mensaje = detalle != null ? detalle.toString() : "Inicia sesión para continuar.";
        escribir(res, ErrorApi.de(401, "NO_AUTENTICADO", mensaje, req.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException e) throws IOException {
        escribir(res, ErrorApi.de(403, "SIN_PERMISO", "Tu rol no tiene permiso para esta acción.", req.getRequestURI()));
    }

    private void escribir(HttpServletResponse res, ErrorApi error) throws IOException {
        res.setStatus(error.status());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), error);
    }
}

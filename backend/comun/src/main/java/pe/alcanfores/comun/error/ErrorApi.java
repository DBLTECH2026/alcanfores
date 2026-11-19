package pe.alcanfores.comun.error;

import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDateTime;
import java.util.List;

/** Cuerpo único de error de todos los microservicios (ver docs/SPEC.md §0). */
public record ErrorApi(LocalDateTime timestamp, int status, String codigo, String mensaje, String ruta,
                       List<ErrorCampo> errores) {

    public static ErrorApi de(int status, String codigo, String mensaje, String ruta, List<ErrorCampo> errores) {
        return new ErrorApi(Tiempo.ahora(), status, codigo, mensaje, ruta, errores == null ? List.of() : errores);
    }

    public static ErrorApi de(int status, String codigo, String mensaje, String ruta) {
        return de(status, codigo, mensaje, ruta, List.of());
    }
}

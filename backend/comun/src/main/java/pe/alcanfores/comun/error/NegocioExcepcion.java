package pe.alcanfores.comun.error;

import java.util.List;

/**
 * Excepción de negocio con estado HTTP, código estable para el front y mensaje en español listo para mostrar.
 * Usar las fábricas: {@link #noEncontrado}, {@link #conflicto}, {@link #regla}, {@link #validacion}, {@link #noDisponible}.
 */
public class NegocioExcepcion extends RuntimeException {

    private final int status;
    private final String codigo;
    private final List<ErrorCampo> errores;

    public NegocioExcepcion(int status, String codigo, String mensaje, List<ErrorCampo> errores) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
        this.errores = errores == null ? List.of() : List.copyOf(errores);
    }

    public NegocioExcepcion(int status, String codigo, String mensaje) {
        this(status, codigo, mensaje, List.of());
    }

    /** 404 · el recurso no existe (o no corresponde al DNI indicado: mismo mensaje para no filtrar datos). */
    public static NegocioExcepcion noEncontrado(String mensaje) {
        return new NegocioExcepcion(404, "NO_ENCONTRADO", mensaje);
    }

    /** 409 · choque con el estado actual (turno ocupado, reseña duplicada, usuario repetido…). */
    public static NegocioExcepcion conflicto(String codigo, String mensaje) {
        return new NegocioExcepcion(409, codigo, mensaje);
    }

    /** 422 · regla de negocio que impide la operación (ya empezó, fuera de plazo, estado no válido…). */
    public static NegocioExcepcion regla(String codigo, String mensaje) {
        return new NegocioExcepcion(422, codigo, mensaje);
    }

    /** 400 · error de validación de un campo concreto detectado en el servicio. */
    public static NegocioExcepcion validacion(String campo, String mensaje) {
        return new NegocioExcepcion(400, "VALIDACION", mensaje, List.of(new ErrorCampo(campo, mensaje)));
    }

    /** 503 · dependencia externa caída o no configurada. */
    public static NegocioExcepcion noDisponible(String codigo, String mensaje) {
        return new NegocioExcepcion(503, codigo, mensaje);
    }

    public int getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public List<ErrorCampo> getErrores() {
        return errores;
    }
}

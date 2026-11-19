package pe.alcanfores.reservas.cliente;

import pe.alcanfores.reservas.dto.DocumentoDtos.DniRespuesta;
import pe.alcanfores.reservas.dto.DocumentoDtos.RucRespuesta;

import java.util.Optional;

/**
 * Proveedor de consultas de documentos (RENIEC para DNI y SUNAT para RUC). Hoy es Factiliza; para cambiar de
 * proveedor basta otra implementación de esta interfaz.
 */
public interface ProveedorDocumentos {

    /** {@code false} si no hay credenciales (el front deja escribir los datos a mano). */
    boolean disponible();

    /** Vacío si el DNI no existe; lanza {@code NegocioExcepcion} 503 si el proveedor falla. */
    Optional<DniRespuesta> consultarDni(String dni);

    /** Vacío si el RUC no existe; lanza {@code NegocioExcepcion} 503 si el proveedor falla. */
    Optional<RucRespuesta> consultarRuc(String ruc);
}

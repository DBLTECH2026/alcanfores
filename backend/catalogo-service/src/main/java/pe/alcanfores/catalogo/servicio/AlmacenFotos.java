package pe.alcanfores.catalogo.servicio;

import org.springframework.core.io.Resource;

import java.util.Optional;

/** Guarda y sirve los archivos de las fotos. Los nombres son siempre UUID + extensión (nunca los del usuario). */
public interface AlmacenFotos {

    /** Nombre nuevo para un archivo: {@code <uuid>.<extension>}. */
    String nombreNuevo(String extension);

    void guardar(String archivo, byte[] contenido);

    /** El archivo, si el nombre es válido y existe; vacío en cualquier otro caso (incluye intentos con «..»). */
    Optional<Resource> leer(String archivo);

    void borrar(String archivo);

    /** true si en disco ya está exactamente este contenido. */
    boolean tieneMismoContenido(String archivo, byte[] contenido);

    /** true si el nombre cumple el formato UUID + extensión permitida. */
    boolean nombreValido(String archivo);
}

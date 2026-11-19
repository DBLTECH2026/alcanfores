package pe.alcanfores.catalogo.servicio;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import pe.alcanfores.catalogo.dto.FotoActualizacionSolicitud;
import pe.alcanfores.catalogo.dto.FotoRespuesta;

public interface FotoServicio {

    /** Tamaño máximo de una foto: 5 MB. */
    long TAMANIO_MAXIMO = 5L * 1024 * 1024;
    /** Lado mínimo (JPG/PNG): 200 px. */
    int LADO_MINIMO = 200;
    /** Fotos máximas por cancha. */
    int MAXIMO_POR_CANCHA = 10;

    FotoRespuesta subir(Long idCancha, MultipartFile archivo, String leyenda);

    FotoRespuesta actualizar(Long idCancha, Long idFoto, FotoActualizacionSolicitud cambios);

    void eliminar(Long idCancha, Long idFoto);

    /** Archivo para servirlo al público; 404 si el nombre no es válido o no existe. */
    ArchivoFoto leer(String archivo);

    record ArchivoFoto(Resource recurso, String tipoContenido) {
    }
}

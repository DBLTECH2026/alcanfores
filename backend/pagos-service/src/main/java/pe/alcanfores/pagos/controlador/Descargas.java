package pe.alcanfores.pagos.controlador;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import pe.alcanfores.pagos.servicio.ComprobanteServicio.Archivo;

import java.nio.charset.StandardCharsets;

/** Respuestas de archivos (PDF en línea, XML como descarga, imágenes privadas). */
final class Descargas {

    private Descargas() {
    }

    static ResponseEntity<byte[]> archivo(Archivo a, boolean enLinea) {
        ContentDisposition disposicion = (enLinea ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(a.nombre(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(a.contenido());
    }

    static ResponseEntity<byte[]> imagenPrivada(String tipoContenido, byte[] contenido) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipoContenido))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(contenido);
    }
}

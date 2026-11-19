package pe.alcanfores.reservas.controlador;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuestas con archivos (PDF y Excel) para los controladores. */
final class Archivos {

    static final MediaType EXCEL = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private Archivos() {
    }

    static ResponseEntity<byte[]> pdf(byte[] contenido, String nombre) {
        return archivo(contenido, nombre, MediaType.APPLICATION_PDF, true);
    }

    static ResponseEntity<byte[]> excel(byte[] contenido, String nombre) {
        return archivo(contenido, nombre, EXCEL, false);
    }

    private static ResponseEntity<byte[]> archivo(byte[] contenido, String nombre, MediaType tipo, boolean enLinea) {
        ContentDisposition disposicion = (enLinea ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(nombre).build();
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(contenido);
    }
}

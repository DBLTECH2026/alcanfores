package pe.alcanfores.reservas.dto;

/** Respuestas de la consulta de documentos (RENIEC/SUNAT). */
public final class DocumentoDtos {

    private DocumentoDtos() {
    }

    public record DniRespuesta(String dni, String nombres, String apellidoPaterno, String apellidoMaterno, String apellidos,
                               String fuente) {

        public DniRespuesta conFuente(String nuevaFuente) {
            return new DniRespuesta(dni, nombres, apellidoPaterno, apellidoMaterno, apellidos, nuevaFuente);
        }
    }

    public record RucRespuesta(String ruc, String razonSocial, String direccion, String estado, String condicion,
                               String fuente) {

        public RucRespuesta conFuente(String nuevaFuente) {
            return new RucRespuesta(ruc, razonSocial, direccion, estado, condicion, nuevaFuente);
        }
    }
}

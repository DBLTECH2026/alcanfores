package pe.alcanfores.notificaciones.cliente;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Cliente de pagos-service: PDF del comprobante simulado ({@code GET /interno/comprobantes/{id}/pdf}). */
@Component
public class PagosCliente {

    static final String URL_PDF = "http://pagos-service/interno/comprobantes/{id}/pdf";

    private final RestClient documentos;
    private final LlamadaProtegida protegida;

    public PagosCliente(@Qualifier("restDocumentos") RestClient documentos, LlamadaProtegida protegida) {
        this.documentos = documentos;
        this.protegida = protegida;
    }

    public byte[] comprobantePdf(Long idComprobante) {
        return protegida.ejecutar("pagos-service", "pagos", () -> documentos.get()
                .uri(URL_PDF, idComprobante)
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .body(byte[].class));
    }
}

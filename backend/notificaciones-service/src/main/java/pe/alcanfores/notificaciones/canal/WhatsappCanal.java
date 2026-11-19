package pe.alcanfores.notificaciones.canal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.alcanfores.notificaciones.cliente.EvolutionCliente;
import pe.alcanfores.notificaciones.cliente.EvolutionExcepcion;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;

import java.util.List;
import java.util.Optional;

/**
 * WhatsApp por Evolution API: primero la tarjeta ilustrada como IMAGEN con el texto como leyenda y después cada PDF
 * como documento con una leyenda breve («Constancia ALC-…», «Boleta B001-…»). Si la imagen falla, el aviso sale igual
 * solo con el texto (no se pierde) y la tarjeta queda NO_DISPONIBLE en la bandeja. Sin tarjeta, o con un texto más
 * largo que la leyenda permitida, se envía el texto solo.
 */
@Component
public class WhatsappCanal implements CanalEnvio {

    /** Largo máximo prudente de la leyenda de una imagen en WhatsApp. */
    static final int MAX_LEYENDA = 1000;
    private static final Logger log = LoggerFactory.getLogger(WhatsappCanal.class);

    private final EvolutionCliente evolution;

    public WhatsappCanal(EvolutionCliente evolution) {
        this.evolution = evolution;
    }

    @Override
    public CanalNotificacion canal() {
        return CanalNotificacion.WHATSAPP;
    }

    @Override
    public boolean configurado() {
        return evolution.configurado();
    }

    @Override
    public void enviar(Notificacion n, List<ArchivoAdjunto> archivos) {
        String texto = n.cuerpoParaEnviar();
        Optional<ArchivoAdjunto> tarjeta = archivos.stream().filter(ArchivoAdjunto::imagen).findFirst();
        try {
            if (tarjeta.isPresent() && texto.length() <= MAX_LEYENDA) {
                enviarTarjetaOTexto(n, tarjeta.get(), texto);
            } else {
                evolution.enviarTexto(n.getDestino(), texto);
            }
            for (ArchivoAdjunto pdf : archivos) {
                if (!pdf.imagen()) {
                    evolution.enviarDocumento(n.getDestino(), pdf.contenido(), pdf.nombre(), pdf.leyenda());
                }
            }
        } catch (EvolutionExcepcion e) {
            throw new EnvioExcepcion(e.getMessage(), e);
        }
    }

    private void enviarTarjetaOTexto(Notificacion n, ArchivoAdjunto tarjeta, String texto) {
        try {
            evolution.enviarImagen(n.getDestino(), tarjeta.contenido(), tarjeta.nombre(), texto);
        } catch (EvolutionExcepcion e) {
            log.warn("La tarjeta {} no se pudo enviar a {} ({}); se envía solo el texto", tarjeta.nombre(), n.getDestino(),
                    e.getMessage());
            n.marcarImagenNoEnviada("No se pudo enviar la imagen (" + e.getMessage() + "); el aviso salió solo con el texto.");
            evolution.enviarTexto(n.getDestino(), texto);
        }
    }
}

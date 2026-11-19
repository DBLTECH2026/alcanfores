package pe.alcanfores.notificaciones.canal;

import org.springframework.stereotype.Component;
import pe.alcanfores.notificaciones.cliente.EvolutionCliente;
import pe.alcanfores.notificaciones.cliente.EvolutionExcepcion;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;

import java.util.List;

/**
 * WhatsApp por Evolution API. Si hay un PDF, el mensaje va como leyenda del documento (un solo envío: si falla, el
 * reintento no duplica el texto). Si el texto es más largo que la leyenda permitida, se envía el texto y luego el PDF.
 */
@Component
public class WhatsappCanal implements CanalEnvio {

    /** Largo máximo prudente de la leyenda de un documento en WhatsApp. */
    static final int MAX_LEYENDA = 1000;

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
    public void enviar(Notificacion n, List<ArchivoPdf> adjuntos) {
        try {
            if (adjuntos.isEmpty()) {
                evolution.enviarTexto(n.getDestino(), n.getCuerpo());
                return;
            }
            boolean textoComoLeyenda = n.getCuerpo().length() <= MAX_LEYENDA;
            if (!textoComoLeyenda) {
                evolution.enviarTexto(n.getDestino(), n.getCuerpo());
            }
            for (int i = 0; i < adjuntos.size(); i++) {
                ArchivoPdf pdf = adjuntos.get(i);
                String leyenda = textoComoLeyenda && i == 0 ? n.getCuerpo() : pdf.nombre();
                evolution.enviarDocumento(n.getDestino(), pdf.contenido(), pdf.nombre(), leyenda);
            }
        } catch (EvolutionExcepcion e) {
            throw new EnvioExcepcion(e.getMessage(), e);
        }
    }
}

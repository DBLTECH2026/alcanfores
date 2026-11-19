package pe.alcanfores.catalogo.mapeador;

import org.springframework.stereotype.Component;
import pe.alcanfores.catalogo.dto.ResenaAdminRespuesta;
import pe.alcanfores.catalogo.dto.ResenaPublicaRespuesta;
import pe.alcanfores.catalogo.modelo.Resena;

@Component
public class ResenaMapeador {

    public ResenaPublicaRespuesta publica(Resena r) {
        return new ResenaPublicaRespuesta(r.getId(), r.getAutor(), r.getPuntuacion(), r.getComentario(), r.getCreadoEn(),
                r.getRespuesta(), r.getRespondidoEn(), true);
    }

    public ResenaAdminRespuesta admin(Resena r) {
        return new ResenaAdminRespuesta(r.getId(), r.getCancha().getId(), r.getCancha().getNombre(), r.getCodigoReserva(),
                r.getAutor(), r.getPuntuacion(), r.getComentario(), r.getEstado().name(), r.getRespuesta(),
                r.getRespondidoEn(), r.getCreadoEn());
    }
}

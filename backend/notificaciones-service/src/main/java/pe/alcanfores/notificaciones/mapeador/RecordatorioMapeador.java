package pe.alcanfores.notificaciones.mapeador;

import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.notificaciones.dto.RecordatorioRespuesta;
import pe.alcanfores.notificaciones.modelo.Recordatorio;

public final class RecordatorioMapeador {

    private RecordatorioMapeador() {
    }

    public static RecordatorioRespuesta aRespuesta(Recordatorio r) {
        DatosReserva d = r.getDatos() == null ? null : r.getDatos().reserva();
        ClienteContacto c = d == null ? null : d.cliente();
        return new RecordatorioRespuesta(r.getId(), r.getCodigoReserva(), r.getMinutosAntes(), c == null ? null : c.nombreCompleto(),
                c == null ? null : c.telefono(), d == null ? null : d.nombreCancha(), r.getInicioReserva(),
                r.getEnviarEn(), r.getEstado().name(), r.getEnviadoEn(), r.getDetalle());
    }
}

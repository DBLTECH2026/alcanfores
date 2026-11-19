package pe.alcanfores.auth.mapeador;

import pe.alcanfores.auth.dto.BitacoraRespuesta;
import pe.alcanfores.auth.modelo.Bitacora;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDateTime;

/** Conversión de eventos de auditoría y filas de bitácora; recorta cada texto al tamaño de su columna. */
public final class BitacoraMapeador {

    private BitacoraMapeador() {
    }

    public static BitacoraRespuesta aRespuesta(Bitacora b) {
        return new BitacoraRespuesta(b.getId(), b.getServicio(), b.getIdActor(), b.getActor(), b.getRol(), b.getAccion(),
                b.getEntidad(), b.getIdEntidad(), b.getDetalle(), b.getIp(), b.getFecha());
    }

    public static Bitacora desdeEvento(AccionAuditadaEvento e) {
        return nueva(e.servicio(), e.idActor(), e.actor(), e.rol(), e.accion(), e.entidad(), e.idEntidad(), e.detalle(),
                e.ip(), e.ocurridoEn());
    }

    public static Bitacora nueva(String servicio, Long idActor, String actor, String rol, String accion, String entidad,
                                 String idEntidad, String detalle, String ip, LocalDateTime fecha) {
        Bitacora b = new Bitacora();
        b.setServicio(recortar(valorO(servicio, "desconocido"), 40));
        b.setIdActor(idActor);
        b.setActor(recortar(valorO(actor, "Sistema"), 60));
        b.setRol(recortar(rol, 20));
        b.setAccion(recortar(valorO(accion, "SIN_ACCION"), 60));
        b.setEntidad(recortar(entidad, 60));
        b.setIdEntidad(recortar(idEntidad, 60));
        b.setDetalle(recortar(detalle, 500));
        b.setIp(recortar(ip, 45));
        b.setFecha(fecha == null ? Tiempo.ahora() : fecha.withNano(0));
        return b;
    }

    static String recortar(String valor, int maximo) {
        String limpio = Texto.limpiar(valor);
        if (limpio == null || limpio.isEmpty()) {
            return null;
        }
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo - 3) + "...";
    }

    private static String valorO(String valor, String porDefecto) {
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}

package pe.alcanfores.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

/** Datos públicos del complejo y sus reglas ({@code GET /api/catalogo/complejo}). */
public record ComplejoRespuesta(
        String nombre,
        String lema,
        String direccion,
        String referencia,
        String whatsapp,
        String correo,
        String mapaUrl,
        BigDecimal latitud,
        BigDecimal longitud,
        Horario horario,
        Reglas reglas,
        Pagos pagos,
        Asistente asistente
) {
    public record Horario(String primerTurno, String ultimoTurno, int duracionMin, String inicioNoche, int diasVisibles) {
    }

    /**
     * {@code recordatorioMinutos}: minutos antes del partido de cada recordatorio, de mayor a menor. {@code recordatorioHoras}
     * se deriva del mayor (compatibilidad con la v1).
     */
    public record Reglas(int adelantoPct, int devolucionHoras, int minutosParaPagar, int recordatorioHoras,
                         List<Integer> recordatorioMinutos, int asistenciaDesdeMin) {
    }

    public record Pagos(String yapeNumero, String yapeTitular, String plinNumero) {
    }

    /** Canchita (asistente con IA): el portal muestra el botón flotante solo si {@code webActiva}. */
    public record Asistente(boolean webActiva) {
    }
}

package pe.alcanfores.catalogo.dto;

import java.math.BigDecimal;

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
        Pagos pagos
) {
    public record Horario(String primerTurno, String ultimoTurno, int duracionMin, String inicioNoche, int diasVisibles) {
    }

    public record Reglas(int adelantoPct, int devolucionHoras, int minutosParaPagar, int recordatorioHoras) {
    }

    public record Pagos(String yapeNumero, String yapeTitular, String plinNumero) {
    }
}

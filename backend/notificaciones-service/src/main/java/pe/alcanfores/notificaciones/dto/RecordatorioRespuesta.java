package pe.alcanfores.notificaciones.dto;

import java.time.LocalDateTime;

/** Fila de la lista de recordatorios del panel. */
public record RecordatorioRespuesta(Long id, String codigoReserva, int minutosAntes, String cliente, String telefono, String cancha,
                                    LocalDateTime inicioReserva, LocalDateTime enviarEn, String estado,
                                    LocalDateTime enviadoEn, String detalle) {
}

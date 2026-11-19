package pe.alcanfores.notificaciones.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Recordatorio «tu partido es en 2 horas»: uno por reserva confirmada y por anticipación (UNIQUE codigo_reserva + minutos_antes). */
@Entity
@Table(name = "recordatorio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_reserva", nullable = false, length = 12)
    private String codigoReserva;

    /** Cuántos minutos antes del inicio se envía (uno de los valores de {@code recordatorio_minutos}). */
    @Column(name = "minutos_antes", nullable = false)
    private int minutosAntes;

    @Column(name = "enviar_en", nullable = false)
    private LocalDateTime enviarEn;

    @Column(name = "inicio_reserva", nullable = false)
    private LocalDateTime inicioReserva;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRecordatorio estado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private DatosRecordatorio datos;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    /** Por qué se canceló u omitió (para el panel). */
    @Column(length = 255)
    private String detalle;

    /**
     * Cuándo ocurrió el evento (confirmación o cambio de horario) del que salen el inicio y los datos; si los toma de
     * reservas al enviar, el momento de esa consulta. Un evento más viejo que esto no los reemplaza.
     */
    @Column(name = "datos_al")
    private LocalDateTime datosAl;

    public Recordatorio(String codigoReserva, int minutosAntes, LocalDateTime inicioReserva, DatosRecordatorio datos,
                        LocalDateTime datosAl) {
        this.codigoReserva = codigoReserva;
        this.minutosAntes = minutosAntes;
        this.enviarEn = inicioReserva.minusMinutes(minutosAntes);
        this.inicioReserva = inicioReserva;
        this.datos = datos;
        this.datosAl = datosAl;
        this.estado = EstadoRecordatorio.PROGRAMADO;
    }

    /**
     * Vuelve a PROGRAMADO para ese inicio con los datos nuevos: la reserva se volvió a confirmar o cambió de horario
     * (un recordatorio ya enviado del horario anterior se envía de nuevo para el nuevo).
     */
    public void reprogramar(LocalDateTime nuevoInicio, DatosRecordatorio nuevosDatos, LocalDateTime nuevosDatosAl) {
        this.enviarEn = nuevoInicio.minusMinutes(minutosAntes);
        this.inicioReserva = nuevoInicio;
        this.datos = nuevosDatos;
        this.datosAl = nuevosDatosAl;
        this.estado = EstadoRecordatorio.PROGRAMADO;
        this.enviadoEn = null;
        this.detalle = null;
    }

    public void cancelar(String motivo) {
        this.estado = EstadoRecordatorio.CANCELADO;
        this.detalle = motivo;
    }

    public void omitir(String motivo) {
        this.estado = EstadoRecordatorio.OMITIDO;
        this.detalle = motivo;
    }

    public void actualizarDatos(DatosRecordatorio nuevosDatos) {
        this.datos = nuevosDatos;
    }

    public boolean programado() {
        return estado == EstadoRecordatorio.PROGRAMADO;
    }
}

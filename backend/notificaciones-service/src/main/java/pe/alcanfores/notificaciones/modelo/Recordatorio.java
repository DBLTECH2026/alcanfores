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

/** Recordatorio «tu partido es en 2 horas»: uno por reserva confirmada ({@code codigo_reserva} único). */
@Entity
@Table(name = "recordatorio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_reserva", nullable = false, unique = true, length = 12)
    private String codigoReserva;

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

    public Recordatorio(String codigoReserva, LocalDateTime enviarEn, LocalDateTime inicioReserva, DatosRecordatorio datos) {
        this.codigoReserva = codigoReserva;
        this.enviarEn = enviarEn;
        this.inicioReserva = inicioReserva;
        this.datos = datos;
        this.estado = EstadoRecordatorio.PROGRAMADO;
    }

    /** Si la reserva se vuelve a confirmar (p. ej. cambio de datos), se reprograma con lo último. */
    public void reprogramar(LocalDateTime nuevoEnviarEn, LocalDateTime nuevoInicio, DatosRecordatorio nuevosDatos) {
        this.enviarEn = nuevoEnviarEn;
        this.inicioReserva = nuevoInicio;
        this.datos = nuevosDatos;
        this.estado = EstadoRecordatorio.PROGRAMADO;
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

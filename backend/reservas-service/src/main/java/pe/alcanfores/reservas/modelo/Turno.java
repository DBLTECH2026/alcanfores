package pe.alcanfores.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Turno de una cancha en una fecha y hora, generado según el horario de la cancha (hoy + días generados). {@code precio}
 * es el precio final (incluye el {@code recargo} de feriado); {@code tipoBloqueo} dice quién lo bloqueó.
 */
@Entity
@Table(name = "turno")
@Getter
@Setter
@NoArgsConstructor
public class Turno {

    public static final String MOTIVO_CANCHA_INACTIVA = "Cancha inactiva";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_turno")
    private Long id;

    @Column(nullable = false)
    private Long idCancha;

    @Column(nullable = false, length = 60)
    private String nombreCancha;

    @Column(nullable = false, length = 20)
    private String deporte;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime horaInicio;

    @Column(nullable = false)
    private LocalTime horaFin;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Franja franja;

    @Column(nullable = false)
    private boolean feriado;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal recargo = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTurno estado = EstadoTurno.DISPONIBLE;

    @Enumerated(EnumType.STRING)
    private TipoBloqueo tipoBloqueo;

    @Column(length = 120)
    private String motivoBloqueo;

    /** Bloquea el turno indicando el tipo y el motivo. */
    public void bloquear(TipoBloqueo tipo, String motivo) {
        estado = EstadoTurno.BLOQUEADO;
        tipoBloqueo = tipo;
        motivoBloqueo = motivo;
    }

    /** Lo deja libre (sin bloqueo). */
    public void liberar() {
        estado = EstadoTurno.DISPONIBLE;
        tipoBloqueo = null;
        motivoBloqueo = null;
    }

    /** Falso solo para los turnos que quedaron fuera del horario de la cancha (no se muestran). */
    public boolean visible() {
        return tipoBloqueo != TipoBloqueo.FUERA_DE_HORARIO;
    }

    public LocalDateTime inicio() {
        return LocalDateTime.of(fecha, horaInicio);
    }

    /** Fin del turno (si termina a medianoche, pasa al día siguiente). */
    public LocalDateTime fin() {
        return horaFin.isAfter(horaInicio) ? LocalDateTime.of(fecha, horaFin) : LocalDateTime.of(fecha.plusDays(1), horaFin);
    }
}

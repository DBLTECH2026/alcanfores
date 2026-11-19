package pe.alcanfores.catalogo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Horario de un día de la semana de una cancha (tabla {@code cancha_horario}). {@code cierre} es la hora en que TERMINA el
 * último turno; medianoche se guarda 23:59:59. Si el día está cerrado se conservan las horas para cuando se vuelva a abrir.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HorarioCancha {

    /** 1 = lunes … 7 = domingo (ISO). */
    @Column(name = "dia_semana", nullable = false)
    private int diaSemana;

    @Column(nullable = false)
    private boolean abierto;

    private LocalTime apertura;

    private LocalTime cierre;
}

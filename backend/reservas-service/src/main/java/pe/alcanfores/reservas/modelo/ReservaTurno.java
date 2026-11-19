package pe.alcanfores.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Un turno de una reserva (una reserva tiene de 1 a 8). La columna generada {@code turno_activo} (no mapeada) + su índice
 * UNIQUE impiden que un turno esté en dos reservas vigentes: {@code vigente} pasa a falso al cancelar o expirar.
 */
@Entity
@Table(name = "reserva_turno")
@Getter
@Setter
@NoArgsConstructor
public class ReservaTurno {

    @EmbeddedId
    private Clave clave = new Clave();

    @MapsId("idReserva")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_reserva")
    private Reserva reserva;

    @MapsId("idTurno")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_turno")
    private Turno turno;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private boolean vigente = true;

    public ReservaTurno(Reserva reserva, Turno turno, BigDecimal precio) {
        this.reserva = reserva;
        this.turno = turno;
        this.precio = precio;
    }

    /** Clave compuesta (id_reserva, id_turno). */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Clave implements Serializable {
        @Column(name = "id_reserva")
        private Long idReserva;
        @Column(name = "id_turno")
        private Long idTurno;

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(idReserva, c.idReserva) && Objects.equals(idTurno, c.idTurno);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idReserva, idTurno);
        }
    }
}

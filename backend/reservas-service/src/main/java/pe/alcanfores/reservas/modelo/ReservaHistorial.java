package pe.alcanfores.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Hito de la línea de tiempo de una reserva (REGISTRADA, COMPROBANTE_RECIBIDO, OBSERVADO, CONFIRMADA…). */
@Entity
@Table(name = "reserva_historial")
@Getter
@Setter
@NoArgsConstructor
public class ReservaHistorial {

    public static final String REGISTRADA = "REGISTRADA";
    public static final String COMPROBANTE_RECIBIDO = "COMPROBANTE_RECIBIDO";
    public static final String OBSERVADO = "OBSERVADO";
    public static final String PAGO_VALIDADO = "PAGO_VALIDADO";
    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String ASISTENCIA = "ASISTENCIA";
    public static final String CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long id;

    @Column(name = "id_reserva", nullable = false)
    private Long idReserva;

    @Column(nullable = false, length = 30)
    private String evento;

    @Column(length = 255)
    private String detalle;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public ReservaHistorial(Long idReserva, String evento, String detalle, LocalDateTime fecha) {
        this.idReserva = idReserva;
        this.evento = evento;
        this.detalle = detalle != null && detalle.length() > 255 ? detalle.substring(0, 252) + "..." : detalle;
        this.fecha = fecha;
    }
}

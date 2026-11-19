package pe.alcanfores.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reserva de un turno. La columna generada {@code turno_activo} (no mapeada) + su índice UNIQUE impiden dos reservas
 * vigentes (PENDIENTE o CONFIRMADA) sobre el mismo turno, incluso si dos solicitudes llegan a la vez.
 */
@Entity
@Table(name = "reserva")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reserva")
    private Long id;

    @Column(nullable = false, length = 12)
    private String codigoReserva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_turno", nullable = false)
    private Turno turno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReserva estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estadoPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Asistencia asistencia = Asistencia.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(length = 20)
    private String metodoPagoPrevisto;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montoAdelanto;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO.setScale(2);

    private LocalDateTime venceEn;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    private LocalDateTime confirmadaEn;

    /** Id del administrador que la registró (null si fue por la web). */
    private Long registradoPor;

    @Column(length = 255)
    private String nota;

    @Column(name = "es_semilla", nullable = false)
    private boolean esSemilla;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean vigente() {
        return estado == EstadoReserva.PENDIENTE || estado == EstadoReserva.CONFIRMADA;
    }

    /** Lo que falta pagar (nunca negativo; 0 si la reserva está cancelada: ya no se cobra nada). */
    public BigDecimal saldo() {
        if (estado == EstadoReserva.CANCELADA) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal s = montoTotal.subtract(montoPagado);
        return s.signum() < 0 ? BigDecimal.ZERO.setScale(2) : s.setScale(2);
    }

    /**
     * Cancelada con devolución: lo devuelto se descuenta de lo pagado ({@code montoPagado} queda neto, 0 si se devolvió
     * todo) y el estado del pago pasa a {@code REEMBOLSADO}.
     */
    public void registrarDevolucion(BigDecimal devuelto) {
        BigDecimal neto = montoPagado.subtract(devuelto);
        montoPagado = (neto.signum() < 0 ? BigDecimal.ZERO : neto).setScale(2);
        estadoPago = EstadoPago.REEMBOLSADO;
    }

    public LocalDateTime inicio() {
        return turno.inicio();
    }
}

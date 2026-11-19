package pe.alcanfores.reservas.modelo;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.BatchSize;
import pe.alcanfores.comun.util.Horarios;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reserva de 1 a 8 turnos de la misma cancha y fecha ({@link ReservaTurno}). {@code turno} es el PRIMER turno (lo usan
 * filtros, reportes y el dashboard). La no doble reserva la garantizan las columnas generadas {@code turno_activo} (no
 * mapeadas) con índice UNIQUE en {@code reserva} (primer turno) y en {@code reserva_turno} (cada turno).
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

    /** Todos los turnos de la reserva (el primero también está en {@link #turno}). */
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL)
    @BatchSize(size = 100)
    private List<ReservaTurno> turnos = new ArrayList<>();

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

    /** Cambia el estado y mantiene la vigencia de sus turnos (una reserva cancelada deja libres todos). */
    public void setEstado(EstadoReserva estado) {
        this.estado = estado;
        boolean v = vigente();
        turnos.forEach(rt -> rt.setVigente(v));
    }

    /** Agrega un turno a la reserva con su precio (el primero que se agrega es también {@link #turno}). */
    public void agregarTurno(Turno t, BigDecimal precio) {
        ReservaTurno rt = new ReservaTurno(this, t, precio);
        rt.setVigente(estado == null || vigente());
        turnos.add(rt);
        if (turno == null) {
            turno = t;
        }
    }

    /** Turnos con su precio, ordenados por hora de inicio. */
    public List<ReservaTurno> turnosConPrecio() {
        return turnos.stream().sorted(Comparator.comparing(rt -> rt.getTurno().getHoraInicio())).toList();
    }

    /** Turnos ordenados por hora de inicio (si la reserva no tiene filas en reserva_turno, solo el primer turno). */
    public List<Turno> turnosOrdenados() {
        if (turnos.isEmpty()) {
            return List.of(turno);
        }
        return turnosConPrecio().stream().map(ReservaTurno::getTurno).toList();
    }

    public int cantidadTurnos() {
        return Math.max(turnos.size(), 1);
    }

    /** Hora en que termina el último turno. */
    public LocalTime horaFin() {
        List<Turno> ts = turnosOrdenados();
        return ts.get(ts.size() - 1).getHoraFin();
    }

    /** Hora de inicio del primer turno. */
    public LocalTime horaInicio() {
        return turnosOrdenados().get(0).getHoraInicio();
    }

    /** «7:00–9:00 p. m.» si son seguidos; «9:00–10:00 a. m. y 7:00–8:00 p. m.» si no. */
    public String horarioTexto() {
        return Horarios.texto(turnosOrdenados().stream()
                .map(t -> new Horarios.Tramo(t.getHoraInicio(), t.getHoraFin())).toList());
    }

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

    /** Inicio del primer turno. */
    public LocalDateTime inicio() {
        return turnosOrdenados().get(0).inicio();
    }
}

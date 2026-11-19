package pe.alcanfores.reservas.modelo;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Un cambio de horario de una reserva: el horario ANTERIOR y el NUEVO (cancha, fecha, horas, turnos y total), quién lo
 * hizo y por qué. Es el historial que muestra el panel y la base del límite de un cambio para el cliente.
 */
@Entity
@Table(name = "reserva_reprogramacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservaReprogramacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reprogramacion")
    private Long id;

    @Column(name = "id_reserva", nullable = false)
    private Long idReserva;

    @Column(nullable = false)
    private Long idCanchaAnterior;
    @Column(nullable = false, length = 60)
    private String canchaAnterior;
    @Column(nullable = false)
    private LocalDate fechaAnterior;
    @Column(nullable = false)
    private LocalTime horaInicioAnterior;
    @Column(nullable = false)
    private LocalTime horaFinAnterior;
    @Column(nullable = false, length = 120)
    private String horarioAnterior;
    @Column(nullable = false, length = 200)
    private String turnosAnteriores;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAnterior;

    @Column(nullable = false)
    private Long idCanchaNueva;
    @Column(nullable = false, length = 60)
    private String canchaNueva;
    @Column(nullable = false)
    private LocalDate fechaNueva;
    @Column(nullable = false)
    private LocalTime horaInicioNueva;
    @Column(nullable = false)
    private LocalTime horaFinNueva;
    @Column(nullable = false, length = 120)
    private String horarioNuevo;
    @Column(nullable = false, length = 200)
    private String turnosNuevos;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalNuevo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActorReprogramacion actor;

    @Column(length = 60)
    private String usuario;

    @Column(length = 200)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime creadoEn;

    /** Fotografía de un horario de la reserva (antes o después del cambio). */
    public record Horario(Long idCancha, String cancha, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                          String horarioTexto, List<Long> idsTurno, BigDecimal total) {
    }

    public ReservaReprogramacion(Long idReserva, Horario antes, Horario despues, ActorReprogramacion actor, String usuario,
                                 String motivo, LocalDateTime creadoEn) {
        this.idReserva = idReserva;
        this.idCanchaAnterior = antes.idCancha();
        this.canchaAnterior = recortar(antes.cancha(), 60);
        this.fechaAnterior = antes.fecha();
        this.horaInicioAnterior = antes.horaInicio();
        this.horaFinAnterior = antes.horaFin();
        this.horarioAnterior = recortar(antes.horarioTexto(), 120);
        this.turnosAnteriores = ids(antes.idsTurno());
        this.totalAnterior = antes.total();
        this.idCanchaNueva = despues.idCancha();
        this.canchaNueva = recortar(despues.cancha(), 60);
        this.fechaNueva = despues.fecha();
        this.horaInicioNueva = despues.horaInicio();
        this.horaFinNueva = despues.horaFin();
        this.horarioNuevo = recortar(despues.horarioTexto(), 120);
        this.turnosNuevos = ids(despues.idsTurno());
        this.totalNuevo = despues.total();
        this.actor = actor;
        this.usuario = recortar(usuario, 60);
        this.motivo = recortar(motivo, 200);
        this.creadoEn = creadoEn;
    }

    public Horario anterior() {
        return new Horario(idCanchaAnterior, canchaAnterior, fechaAnterior, horaInicioAnterior, horaFinAnterior,
                horarioAnterior, lista(turnosAnteriores), totalAnterior);
    }

    public Horario nuevo() {
        return new Horario(idCanchaNueva, canchaNueva, fechaNueva, horaInicioNueva, horaFinNueva, horarioNuevo,
                lista(turnosNuevos), totalNuevo);
    }

    private static String ids(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Long> lista(String ids) {
        return ids == null || ids.isBlank() ? List.of() : Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
    }

    private static String recortar(String texto, int max) {
        return texto != null && texto.length() > max ? texto.substring(0, max) : texto;
    }
}

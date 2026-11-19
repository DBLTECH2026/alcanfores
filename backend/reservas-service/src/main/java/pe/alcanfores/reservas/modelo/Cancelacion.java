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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Cancelación de una reserva (una por reserva): quién, por qué y si corresponde devolución. */
@Entity
@Table(name = "cancelacion")
@Getter
@Setter
@NoArgsConstructor
public class Cancelacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cancelacion")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_reserva", nullable = false)
    private Reserva reserva;

    private Long idAdministrador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanceladoPor canceladoPor;

    @Column(nullable = false, length = 255)
    private String motivo;

    @Column(nullable = false)
    private boolean devolucion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montoDevolucion = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false)
    private LocalDateTime fechaCancelacion;
}

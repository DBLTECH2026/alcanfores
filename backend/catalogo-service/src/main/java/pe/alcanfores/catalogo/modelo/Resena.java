package pe.alcanfores.catalogo.modelo;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDateTime;

/** Reseña verificada: solo la deja quien jugó (lo confirma reservas-service). Una por reserva. */
@Entity
@Table(name = "resena")
@Getter
@Setter
@NoArgsConstructor
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resena")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cancha", nullable = false)
    private Cancha cancha;

    /** Reserva que habilitó la reseña (origen CLIENTE); nulo en las del complejo. */
    @Column(name = "codigo_reserva", length = 12, unique = true)
    private String codigoReserva;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenResena origen = OrigenResena.CLIENTE;

    /** Cuenta del cliente que la escribió (id de auth-service); nulo en las antiguas y en las del complejo. */
    @Column(name = "id_cuenta_cliente")
    private Long idCuentaCliente;

    @Column(nullable = false, length = 60)
    private String autor;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer puntuacion;

    @Column(nullable = false, length = 500)
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoResena estado = EstadoResena.PUBLICADA;

    @Column(length = 500)
    private String respuesta;

    @Column(name = "respondido_en")
    private LocalDateTime respondidoEn;

    /** Fecha de la reseña; en las del complejo la elige el administrador. */
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = Tiempo.ahora();
        }
    }

    public boolean delComplejo() {
        return origen == OrigenResena.COMPLEJO;
    }
}

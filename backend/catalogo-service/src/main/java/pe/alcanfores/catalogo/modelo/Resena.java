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

    @Column(name = "codigo_reserva", nullable = false, length = 12, unique = true)
    private String codigoReserva;

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

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = Tiempo.ahora();
    }
}

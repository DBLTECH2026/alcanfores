package pe.alcanfores.catalogo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDateTime;

@Entity
@Table(name = "cancha_foto")
@Getter
@Setter
@NoArgsConstructor
public class CanchaFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_foto")
    private Long id;

    @Column(name = "id_cancha", nullable = false)
    private Long idCancha;

    /** Nombre en disco: UUID + extensión según los bytes mágicos (nunca el nombre que manda el usuario). */
    @Column(nullable = false, length = 60, unique = true)
    private String archivo;

    @Column(length = 120)
    private String leyenda;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false)
    private boolean principal;

    private Integer ancho;

    private Integer alto;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = Tiempo.ahora();
    }
}

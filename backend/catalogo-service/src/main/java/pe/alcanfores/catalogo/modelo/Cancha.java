package pe.alcanfores.catalogo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.alcanfores.comun.util.Tiempo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cancha")
@Getter
@Setter
@NoArgsConstructor
public class Cancha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cancha")
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(nullable = false, length = 60, unique = true)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Deporte deporte;

    @Column(nullable = false, length = 40)
    private String superficie;

    @Column(nullable = false)
    private Integer capacidad;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(name = "descripcion_larga", columnDefinition = "TEXT")
    private String descripcionLarga;

    @Column(nullable = false)
    private boolean techada;

    @Column(name = "iluminacion_led", nullable = false)
    private boolean iluminacionLed;

    @Column(length = 40)
    private String medidas;

    /** Lista de textos guardada como JSON (por ejemplo «Camerinos con duchas»). */
    @Convert(converter = ListaTextoJson.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "caracteristicas", columnDefinition = "json")
    private List<String> caracteristicas = new ArrayList<>();

    @Column(name = "precio_dia", nullable = false, precision = 6, scale = 2)
    private BigDecimal precioDia;

    @Column(name = "precio_noche", nullable = false, precision = 6, scale = 2)
    private BigDecimal precioNoche;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCancha estado = EstadoCancha.ACTIVA;

    @Column(nullable = false)
    private Integer orden;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = Tiempo.ahora();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Tiempo.ahora();
    }

    public boolean estaActiva() {
        return estado == EstadoCancha.ACTIVA;
    }
}

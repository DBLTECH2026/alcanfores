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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Feriado o día no laborable del calendario del complejo (uno por fecha). */
@Entity
@Table(name = "dia_especial")
@Getter
@Setter
@NoArgsConstructor
public class DiaEspecial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDiaEspecial tipo;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** Ids de las canchas que cierra un NO_LABORABLE; {@code null} = todas. */
    @Convert(converter = ListaIdsJson.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "canchas", columnDefinition = "json")
    private List<Long> canchas;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    public DiaEspecial(LocalDate fecha, TipoDiaEspecial tipo, String nombre, List<Long> canchas) {
        this.fecha = fecha;
        this.tipo = tipo;
        this.nombre = nombre;
        this.canchas = canchas;
    }

    @PrePersist
    void alCrear() {
        creadoEn = Tiempo.ahora();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Tiempo.ahora();
    }
}

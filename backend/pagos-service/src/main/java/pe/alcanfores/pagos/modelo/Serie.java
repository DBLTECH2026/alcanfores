package pe.alcanfores.pagos.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Serie de comprobantes (B001, F001, BC01, FC01) con su último correlativo emitido. */
@Entity
@Table(name = "serie")
@Getter
@Setter
@NoArgsConstructor
public class Serie {

    @Id
    @Column(length = 4)
    private String serie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoComprobante tipo;

    @Column(name = "ultimo_correlativo", nullable = false)
    private int ultimoCorrelativo;
}

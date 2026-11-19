package pe.alcanfores.pagos.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A quién se emite el comprobante de un pago. Se registra con el pago (Yape/Plin, caja) o con el intento en línea
 * (y se enlaza al pago cuando la pasarela lo aprueba).
 */
@Entity
@Table(name = "datos_facturacion")
@Getter
@Setter
@NoArgsConstructor
public class DatosFacturacion {

    public static final String DOC_DNI = "1";
    public static final String DOC_RUC = "6";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_reserva", nullable = false, length = 12)
    private String codigoReserva;

    @Column(name = "id_pago")
    private Long idPago;

    @Column(name = "referencia_intento", length = 14)
    private String referenciaIntento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoComprobante tipo;

    /** Catálogo 06 de SUNAT: «1» DNI, «6» RUC. */
    @Column(name = "tipo_doc", nullable = false, length = 1)
    private String tipoDoc;

    @Column(name = "num_doc", nullable = false, length = 11)
    private String numDoc;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 200)
    private String direccion;
}

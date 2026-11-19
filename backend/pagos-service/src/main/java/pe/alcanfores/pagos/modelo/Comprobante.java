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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Boleta, factura o nota de crédito SIMULADA (sin validez tributaria). */
@Entity
@Table(name = "comprobante")
@Getter
@Setter
@NoArgsConstructor
public class Comprobante {

    public static final String ESTADO_SUNAT_SIMULADO = "ACEPTADO (SIMULADO)";
    /** Caracteres del hash que se muestran como «valor resumen» (como el DigestValue de los comprobantes reales). */
    public static final int LARGO_VALOR_RESUMEN = 28;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comprobante")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoComprobante tipo;

    @Column(name = "codigo_tipo", nullable = false, length = 2)
    private String codigoTipo;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false)
    private int correlativo;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "codigo_reserva", nullable = false, length = 12)
    private String codigoReserva;

    @Column(name = "id_pago", nullable = false)
    private Long idPago;

    @Column(name = "cliente_tipo_doc", nullable = false, length = 1)
    private String clienteTipoDoc;

    @Column(name = "cliente_num_doc", nullable = false, length = 11)
    private String clienteNumDoc;

    @Column(name = "cliente_nombre", nullable = false, length = 150)
    private String clienteNombre;

    @Column(name = "cliente_direccion", length = 200)
    private String clienteDireccion;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(name = "op_gravada", nullable = false, precision = 10, scale = 2)
    private BigDecimal opGravada;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "monto_letras", nullable = false, length = 200)
    private String montoLetras;

    /** SHA-256 (base64) del XML UBL generado. */
    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "cadena_qr", nullable = false, length = 255)
    private String cadenaQr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoComprobante estado;

    @Column(name = "estado_sunat", nullable = false, length = 40)
    private String estadoSunat;

    /** Para notas de crédito: comprobante que modifican. */
    @Column(name = "id_referencia")
    private Long idReferencia;

    @Column(length = 255)
    private String motivo;

    /** Catálogo 09 de SUNAT (01 anulación de la operación, 06 devolución total). */
    @Column(name = "codigo_motivo", length = 2)
    private String codigoMotivo;

    @Column(name = "emisor_ruc", nullable = false, length = 11)
    private String emisorRuc;

    @Column(name = "emisor_razon_social", nullable = false, length = 150)
    private String emisorRazonSocial;

    @Column(name = "emisor_direccion", nullable = false, length = 200)
    private String emisorDireccion;

    /** «B001-00000012» */
    public String serieNumero() {
        return formatear(serie, correlativo);
    }

    public String valorResumen() {
        return hash == null ? null : hash.substring(0, Math.min(LARGO_VALOR_RESUMEN, hash.length()));
    }

    public static String formatear(String serie, int correlativo) {
        return serie + "-" + String.format("%08d", correlativo);
    }
}

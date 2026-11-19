package pe.alcanfores.pagos.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

/** Sesión de pago en línea (checkout) con la pasarela beta o con Mercado Pago. Referencia «ALP-XXXXXXXXXX». */
@Entity
@Table(name = "intento_pago")
@Getter
@Setter
@NoArgsConstructor
public class IntentoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 14, unique = true)
    private String referencia;

    @Column(name = "codigo_reserva", nullable = false, length = 12)
    private String codigoReserva;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProveedorPasarela proveedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false)
    private TipoPago tipoPago;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoIntento estado;

    @Column(name = "motivo_rechazo", length = 160)
    private String motivoRechazo;

    /** Id del proveedor: transacción de la pasarela beta, preferencia o pago de Mercado Pago. */
    @Column(name = "id_externo", length = 80)
    private String idExterno;

    @Column(name = "url_checkout", length = 500)
    private String urlCheckout;

    @Column(name = "id_pago")
    private Long idPago;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Embedded
    private InstantaneaReserva reserva;
}

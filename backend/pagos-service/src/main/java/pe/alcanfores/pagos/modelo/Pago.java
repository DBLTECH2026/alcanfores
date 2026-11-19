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

/** Un cobro de la reserva (adelanto, total o saldo) por cualquier canal. */
@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long id;

    @Column(name = "codigo_reserva", nullable = false, length = 12)
    private String codigoReserva;

    @Column(name = "id_administrador")
    private Long idAdministrador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalPago canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false)
    private TipoPago tipoPago;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "codigo_operacion", length = 40)
    private String codigoOperacion;

    /** Nombre UUID del archivo en {@code uploads/comprobantes} (nunca una ruta del usuario). */
    @Column(name = "archivo_comprobante", length = 60)
    private String archivoComprobante;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacion", nullable = false)
    private EstadoValidacion estado;

    @Column(length = 255)
    private String observacion;

    @Column(name = "referencia_pasarela", length = 20)
    private String referenciaPasarela;

    @Column(name = "marca_tarjeta", length = 20)
    private String marcaTarjeta;

    @Column(length = 4)
    private String ultimos4;

    @Column(name = "codigo_autorizacion", length = 12)
    private String codigoAutorizacion;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    @Column(name = "validado_por", length = 60)
    private String validadoPor;

    /** Lectura de la foto con la que Canchita (IA) validó el pago (JSON o texto); null si lo validó una persona. */
    @Column(name = "lectura_ia", columnDefinition = "text")
    private String lecturaIa;

    @Embedded
    private InstantaneaReserva reserva;

    public static Pago nuevo(String codigoReserva, InstantaneaReserva reserva, MetodoPago metodo, CanalPago canal,
                             TipoPago tipoPago, BigDecimal monto, EstadoValidacion estado, LocalDateTime fechaEnvio) {
        Pago p = new Pago();
        p.setCodigoReserva(codigoReserva);
        p.setReserva(reserva);
        p.setMetodo(metodo);
        p.setCanal(canal);
        p.setTipoPago(tipoPago);
        p.setMonto(monto.setScale(2, java.math.RoundingMode.HALF_UP));
        p.setEstado(estado);
        p.setFechaEnvio(fechaEnvio);
        return p;
    }

    /** Marca el pago como cobrado y validado por alguien (usuario del panel, «Pasarela» o «Mercado Pago»). */
    public void validar(LocalDateTime momento, Long idAdministrador, String validadoPor) {
        this.estado = EstadoValidacion.VALIDADO;
        this.fechaValidacion = momento;
        this.idAdministrador = idAdministrador;
        this.validadoPor = validadoPor;
    }
}

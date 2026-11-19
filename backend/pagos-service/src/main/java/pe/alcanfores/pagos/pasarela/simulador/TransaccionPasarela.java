package pe.alcanfores.pagos.pasarela.simulador;

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

/**
 * Libro del PROVEEDOR simulado «Alcanfores Pay (beta)»: cada cobro que procesa y su aviso pendiente al comercio
 * (patrón outbox: si el webhook falla, se reintenta). Nunca guarda el número completo ni el CVV.
 */
@Entity
@Table(name = "pasarela_transaccion")
@Getter
@Setter
@NoArgsConstructor
public class TransaccionPasarela {

    public enum Metodo { TARJETA, YAPE }

    public enum Estado { APROBADA, RECHAZADA, PENDIENTE_3DS, ANULADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_transaccion", nullable = false, length = 36, unique = true)
    private String idTransaccion;

    @Column(nullable = false, length = 14)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Metodo metodo;

    @Column(length = 20)
    private String marca;

    @Column(length = 4)
    private String ultimos4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(length = 160)
    private String motivo;

    @Column(name = "codigo_autorizacion", length = 6)
    private String codigoAutorizacion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    /** Id del aviso (cabecera X-Evento-Id): el mismo en cada reintento para que el comercio lo procese una vez. */
    @Column(name = "id_evento", length = 36)
    private String idEvento;

    @Column(name = "cuerpo_notificacion", columnDefinition = "text")
    private String cuerpoNotificacion;

    @Column(nullable = false)
    private boolean notificada;

    @Column(name = "intentos_notificacion", nullable = false)
    private int intentosNotificacion;

    @Column(name = "ultimo_error", length = 255)
    private String ultimoError;

    @Column(name = "proximo_intento")
    private LocalDateTime proximoIntento;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}

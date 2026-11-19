package pe.alcanfores.auth.modelo;

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

import java.time.LocalDateTime;

/**
 * Código de 6 dígitos para cambiar la contraseña (cliente del portal o usuario del panel). Solo se guardan hashes:
 * el del código y, una vez verificado, el del permiso de un solo uso para restablecer.
 */
@Entity
@Table(name = "codigo_recuperacion")
@Getter
@Setter
@NoArgsConstructor
public class CodigoRecuperacion {

    public enum TipoCuenta { CLIENTE, ADMIN }

    public enum Canal { CORREO, WHATSAPP }

    /** PENDIENTE (enviado) → VERIFICADO (código correcto, permiso emitido) → USADO; ANULADO si se reemplaza o se agota. */
    public enum Estado { PENDIENTE, VERIFICADO, USADO, ANULADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cuenta", nullable = false, columnDefinition = "enum('CLIENTE','ADMIN')")
    private TipoCuenta tipoCuenta;

    @Column(name = "id_cuenta", nullable = false)
    private Long idCuenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('CORREO','WHATSAPP')")
    private Canal canal;

    @Column(nullable = false, length = 120)
    private String destino;

    @Column(name = "codigo_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String codigoHash;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('PENDIENTE','VERIFICADO','USADO','ANULADO')")
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "token_hash", length = 64, unique = true, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "token_expira_en")
    private LocalDateTime tokenExpiraEn;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    @Column(length = 45)
    private String ip;

    public boolean vencido(LocalDateTime ahora) {
        return !expiraEn.isAfter(ahora);
    }
}

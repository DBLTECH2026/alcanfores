package pe.alcanfores.notificaciones.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Servidor de correo que el dueño configura desde el panel (una sola fila, {@code id = 1}). La contraseña se guarda
 * cifrada con AES-GCM y nunca se devuelve ni se escribe en los logs.
 */
@Entity
@Table(name = "config_correo")
@Getter
@Setter
@NoArgsConstructor
public class ConfigCorreo {

    public static final byte ID_UNICO = 1;

    @Id
    private Byte id = ID_UNICO;

    @Column(nullable = false, length = 120)
    private String host;

    @Column(nullable = false)
    private int puerto;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Column(name = "clave_cifrada", length = 600)
    private String claveCifrada;

    @Column(nullable = false, length = 100)
    private String remitente;

    @Column(name = "nombre_remitente", nullable = false, length = 80)
    private String nombreRemitente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('SSL','STARTTLS')")
    private SeguridadCorreo seguridad;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 60)
    private String actualizadoPor;

    public boolean tieneClave() {
        return claveCifrada != null && !claveCifrada.isBlank();
    }

    @Override
    public String toString() {
        return "ConfigCorreo[" + host + ":" + puerto + " " + seguridad + ", usuario=" + usuario + "]";
    }
}

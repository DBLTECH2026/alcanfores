package pe.alcanfores.auth.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDateTime;

/**
 * Cuenta de un cliente del portal. El DNI es el vínculo con sus reservas (incluidas las que hizo antes de registrarse).
 * La contraseña solo se guarda como hash BCrypt; mismo bloqueo por intentos que los administradores.
 */
@Entity
@Table(name = "cuenta_cliente")
@Getter
@Setter
@NoArgsConstructor
public class CuentaCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8, unique = true, columnDefinition = "char(8)")
    private String dni;

    @Column(nullable = false, length = 60)
    private String nombres;

    @Column(nullable = false, length = 60)
    private String apellidos;

    @Column(nullable = false, length = 100, unique = true)
    private String correo;

    @Column(nullable = false, length = 9, columnDefinition = "char(9)")
    private String telefono;

    @Column(name = "contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('ACTIVO','INACTIVO')")
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @PrePersist
    void alCrear() {
        creadoEn = Tiempo.ahora();
    }

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }

    public boolean estaActiva() {
        return estado == EstadoUsuario.ACTIVO;
    }

    public boolean estaBloqueada(LocalDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /** Igual que {@link Administrador#registrarIntentoFallido}: {@code true} si este intento dejó la cuenta bloqueada. */
    public boolean registrarIntentoFallido(LocalDateTime ahora, int maximo, int minutosBloqueo) {
        intentosFallidos++;
        if (intentosFallidos >= maximo) {
            intentosFallidos = 0;
            bloqueadoHasta = ahora.plusMinutes(minutosBloqueo);
            return true;
        }
        return false;
    }

    public void registrarAccesoExitoso(LocalDateTime ahora) {
        intentosFallidos = 0;
        bloqueadoHasta = null;
        ultimoAcceso = ahora;
    }
}

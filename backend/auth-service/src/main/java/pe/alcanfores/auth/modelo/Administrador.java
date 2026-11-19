package pe.alcanfores.auth.modelo;

import jakarta.persistence.Column;
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
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDateTime;

/** Usuario del panel (ADMIN o RECEPCION). La contraseña solo se guarda como hash BCrypt. */
@Entity
@Table(name = "administrador")
@Getter
@Setter
@NoArgsConstructor
public class Administrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_administrador")
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombres;

    @Column(nullable = false, length = 60)
    private String apellidos;

    @Column(nullable = false, length = 30, unique = true)
    private String usuario;

    @Column(nullable = false, length = 100, unique = true)
    private String correo;

    @Column(name = "contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('ADMIN','RECEPCION')")
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('ACTIVO','INACTIVO')")
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    void alCrear() {
        LocalDateTime ahora = Tiempo.ahora();
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Tiempo.ahora();
    }

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }

    public boolean estaActivo() {
        return estado == EstadoUsuario.ACTIVO;
    }

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }

    public boolean estaBloqueado(LocalDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /**
     * Suma un intento fallido; al llegar al máximo bloquea la cuenta y reinicia el contador.
     *
     * @return {@code true} si este intento dejó la cuenta bloqueada
     */
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

    /** Tras restablecer la contraseña, la cuenta queda desbloqueada y sin intentos pendientes. */
    public void desbloquear() {
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }
}

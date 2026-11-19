package pe.alcanfores.auth.dto;

import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;

import java.time.LocalDateTime;

/** Usuario del panel tal como lo ve la pantalla «Usuarios» (nunca incluye el hash de la contraseña). */
public record UsuarioRespuesta(Long id, String nombres, String apellidos, String usuario, String correo, String telefono, Rol rol,
                               EstadoUsuario estado, boolean bloqueado, LocalDateTime bloqueadoHasta,
                               LocalDateTime ultimoAcceso, LocalDateTime creadoEn, LocalDateTime actualizadoEn) {
}

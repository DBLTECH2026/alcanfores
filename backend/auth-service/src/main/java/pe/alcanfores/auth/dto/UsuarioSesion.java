package pe.alcanfores.auth.dto;

import pe.alcanfores.auth.modelo.Rol;

/** Datos del usuario que el front guarda tras el login. */
public record UsuarioSesion(Long id, String nombres, String apellidos, String usuario, String correo, String telefono,
                            Rol rol) {
}

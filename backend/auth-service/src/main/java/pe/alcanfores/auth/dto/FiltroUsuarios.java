package pe.alcanfores.auth.dto;

import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;

/** Filtros de la lista de usuarios: {@code q} busca en nombres, apellidos, usuario y correo. */
public record FiltroUsuarios(String q, Rol rol, EstadoUsuario estado) {
}

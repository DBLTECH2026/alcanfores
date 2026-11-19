package pe.alcanfores.auth.servicio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.alcanfores.auth.dto.EditarUsuarioSolicitud;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.auth.repositorio.AdministradorRepositorio;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.UsuarioToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de autoprotección que no se pueden armar fácilmente por HTTP (token con rol desactualizado). */
@ExtendWith(MockitoExtension.class)
class UsuarioServicioImplTest {

    @Mock
    private AdministradorRepositorio administradores;
    @Mock
    private PasswordEncoder codificador;
    @Mock
    private BitacoraServicio bitacora;
    @InjectMocks
    private UsuarioServicioImpl servicio;

    private final UsuarioToken actor = new UsuarioToken(99L, "otro", "Otro Admin", "ADMIN");

    private static Administrador admin(long id) {
        Administrador a = new Administrador();
        a.setId(id);
        a.setNombres("Rosa");
        a.setApellidos("Huamán");
        a.setUsuario("rhuaman");
        a.setCorreo("rhuaman@alcanfores.pe");
        a.setRol(Rol.ADMIN);
        a.setEstado(EstadoUsuario.ACTIVO);
        return a;
    }

    @Test
    void noSePuedeQuitarElRolAlUltimoAdminActivo() {
        when(administradores.findById(1L)).thenReturn(Optional.of(admin(1L)));
        when(administradores.countByRolAndEstado(Rol.ADMIN, EstadoUsuario.ACTIVO)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.editar(1L, new EditarUsuarioSolicitud("Rosa", "Huamán", "rhuaman",
                "rhuaman@alcanfores.pe", Rol.RECEPCION, null), actor))
                .isInstanceOf(NegocioExcepcion.class)
                .satisfies(e -> {
                    NegocioExcepcion n = (NegocioExcepcion) e;
                    assertThat(n.getStatus()).isEqualTo(422);
                    assertThat(n.getCodigo()).isEqualTo("ULTIMO_ADMIN");
                });
        verify(administradores, never()).save(any());
    }

    @Test
    void noSePuedeDesactivarAlUltimoAdminActivo() {
        when(administradores.findById(1L)).thenReturn(Optional.of(admin(1L)));
        when(administradores.countByRolAndEstado(Rol.ADMIN, EstadoUsuario.ACTIVO)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.cambiarEstado(1L, EstadoUsuario.INACTIVO, actor))
                .isInstanceOf(NegocioExcepcion.class)
                .hasMessage("Debe quedar al menos un administrador activo.");
    }

    @Test
    void cambiarAlMismoEstadoNoHaceNada() {
        when(administradores.findById(1L)).thenReturn(Optional.of(admin(1L)));

        assertThat(servicio.cambiarEstado(1L, EstadoUsuario.ACTIVO, actor).estado()).isEqualTo(EstadoUsuario.ACTIVO);
        verify(administradores, never()).save(any());
        verify(bitacora, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void editarSinCambiosNoEscribeEnLaBitacora() {
        Administrador a = admin(1L);
        when(administradores.findById(1L)).thenReturn(Optional.of(a));

        servicio.editar(1L, new EditarUsuarioSolicitud("Rosa", "Huamán", "rhuaman", "rhuaman@alcanfores.pe", Rol.ADMIN, null), actor);

        verify(administradores).save(a);
        verify(bitacora, never()).registrar(any(), any(), any(), any());
    }
}

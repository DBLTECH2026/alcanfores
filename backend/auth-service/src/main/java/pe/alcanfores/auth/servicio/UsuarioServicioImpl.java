package pe.alcanfores.auth.servicio;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.auth.dto.CrearUsuarioSolicitud;
import pe.alcanfores.auth.dto.EditarUsuarioSolicitud;
import pe.alcanfores.auth.dto.FiltroUsuarios;
import pe.alcanfores.auth.dto.UsuarioRespuesta;
import pe.alcanfores.auth.mapeador.UsuarioMapeador;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.auth.repositorio.AdministradorRepositorio;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class UsuarioServicioImpl implements UsuarioServicio {

    static final String ENTIDAD = "administrador";
    private static final Set<String> ORDEN_PERMITIDO = Set.of("nombres", "apellidos", "usuario", "correo", "rol", "estado",
            "ultimoAcceso", "creadoEn");
    private static final Sort ORDEN_DEFECTO = Sort.by("nombres", "apellidos");

    private final AdministradorRepositorio administradores;
    private final PasswordEncoder codificador;
    private final BitacoraServicio bitacora;

    public UsuarioServicioImpl(AdministradorRepositorio administradores, PasswordEncoder codificador,
                               BitacoraServicio bitacora) {
        this.administradores = administradores;
        this.codificador = codificador;
        this.bitacora = bitacora;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<UsuarioRespuesta> listar(FiltroUsuarios filtro, Pageable pagina) {
        Pageable p = Paginacion.sanear(pagina, ORDEN_PERMITIDO, ORDEN_DEFECTO);
        return PaginaRespuesta.de(administradores.findAll(especificacion(filtro), p), UsuarioMapeador::aRespuesta);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioRespuesta obtener(Long id) {
        return UsuarioMapeador.aRespuesta(buscar(id));
    }

    @Override
    @Transactional
    public UsuarioRespuesta crear(CrearUsuarioSolicitud s) {
        String usuario = s.usuario().trim();
        String correo = Texto.correo(s.correo());
        if (administradores.existsByUsuario(usuario)) {
            throw usuarioRepetido();
        }
        if (administradores.existsByCorreo(correo)) {
            throw correoRepetido();
        }
        Administrador a = new Administrador();
        a.setNombres(Texto.titulo(s.nombres()));
        a.setApellidos(Texto.titulo(s.apellidos()));
        a.setUsuario(usuario);
        a.setCorreo(correo);
        a.setRol(s.rol());
        a.setEstado(EstadoUsuario.ACTIVO);
        a.setContrasenaHash(codificador.encode(s.contrasena()));
        administradores.save(a);
        bitacora.registrar("CREAR_USUARIO", ENTIDAD, a.getId(),
                "Usuario " + a.getUsuario() + " (" + a.nombreCompleto() + ") con rol " + a.getRol());
        return UsuarioMapeador.aRespuesta(a);
    }

    @Override
    @Transactional
    public UsuarioRespuesta editar(Long id, EditarUsuarioSolicitud s, UsuarioToken actor) {
        Administrador a = buscar(id);
        String usuario = s.usuario().trim();
        String correo = Texto.correo(s.correo());
        if (a.esAdmin() && s.rol() != Rol.ADMIN) {
            if (Objects.equals(a.getId(), actor.id())) {
                throw NegocioExcepcion.regla("AUTOPROTECCION", "No puedes quitarte tu propio rol de administrador.");
            }
            exigirOtroAdminActivo(a);
        }
        if (administradores.existsByUsuarioAndIdNot(usuario, id)) {
            throw usuarioRepetido();
        }
        if (administradores.existsByCorreoAndIdNot(correo, id)) {
            throw correoRepetido();
        }
        List<String> cambios = new ArrayList<>();
        anotar(cambios, "nombres", a.getNombres(), Texto.titulo(s.nombres()));
        anotar(cambios, "apellidos", a.getApellidos(), Texto.titulo(s.apellidos()));
        anotar(cambios, "usuario", a.getUsuario(), usuario);
        anotar(cambios, "correo", a.getCorreo(), correo);
        anotar(cambios, "rol", a.getRol().name(), s.rol().name());
        a.setNombres(Texto.titulo(s.nombres()));
        a.setApellidos(Texto.titulo(s.apellidos()));
        a.setUsuario(usuario);
        a.setCorreo(correo);
        a.setRol(s.rol());
        administradores.save(a);
        if (!cambios.isEmpty()) {
            bitacora.registrar("EDITAR_USUARIO", ENTIDAD, a.getId(), a.getUsuario() + ": " + String.join("; ", cambios));
        }
        return UsuarioMapeador.aRespuesta(a);
    }

    @Override
    @Transactional
    public UsuarioRespuesta cambiarEstado(Long id, EstadoUsuario estado, UsuarioToken actor) {
        Administrador a = buscar(id);
        if (a.getEstado() == estado) {
            return UsuarioMapeador.aRespuesta(a);
        }
        if (estado == EstadoUsuario.INACTIVO) {
            if (Objects.equals(a.getId(), actor.id())) {
                throw NegocioExcepcion.regla("AUTOPROTECCION", "No puedes desactivar tu propio usuario.");
            }
            if (a.esAdmin()) {
                exigirOtroAdminActivo(a);
            }
        }
        a.setEstado(estado);
        administradores.save(a);
        bitacora.registrar(estado == EstadoUsuario.ACTIVO ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO", ENTIDAD, a.getId(),
                "Usuario " + a.getUsuario() + " ahora está " + estado);
        return UsuarioMapeador.aRespuesta(a);
    }

    @Override
    @Transactional
    public void restablecerContrasena(Long id, String nueva) {
        Administrador a = buscar(id);
        boolean estabaBloqueado = a.estaBloqueado(Tiempo.ahora());
        a.setContrasenaHash(codificador.encode(nueva));
        a.desbloquear();
        administradores.save(a);
        bitacora.registrar("RESTABLECER_CONTRASENA", ENTIDAD, a.getId(), "Contraseña restablecida de " + a.getUsuario()
                + (estabaBloqueado ? " (cuenta desbloqueada)" : ""));
    }

    private Administrador buscar(Long id) {
        return administradores.findById(id)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos ese usuario."));
    }

    private void exigirOtroAdminActivo(Administrador a) {
        long adminsActivos = administradores.countByRolAndEstado(Rol.ADMIN, EstadoUsuario.ACTIVO);
        long restantes = a.estaActivo() ? adminsActivos - 1 : adminsActivos;
        if (restantes < 1) {
            throw NegocioExcepcion.regla("ULTIMO_ADMIN", "Debe quedar al menos un administrador activo.");
        }
    }

    private static void anotar(List<String> cambios, String campo, String antes, String despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(campo + ": " + antes + " -> " + despues);
        }
    }

    private static NegocioExcepcion usuarioRepetido() {
        return NegocioExcepcion.conflicto("USUARIO_DUPLICADO", "Ese usuario ya está en uso. Elige otro.");
    }

    private static NegocioExcepcion correoRepetido() {
        return NegocioExcepcion.conflicto("CORREO_DUPLICADO", "Ese correo ya está registrado en otro usuario.");
    }

    private static Specification<Administrador> especificacion(FiltroUsuarios f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                String patron = Paginacion.patronContiene(f.q());
                p.add(cb.or(
                        cb.like(cb.lower(root.get("nombres")), patron, '\\'),
                        cb.like(cb.lower(root.get("apellidos")), patron, '\\'),
                        cb.like(cb.lower(root.get("usuario")), patron, '\\'),
                        cb.like(cb.lower(root.get("correo")), patron, '\\')));
            }
            if (f.rol() != null) {
                p.add(cb.equal(root.get("rol"), f.rol()));
            }
            if (f.estado() != null) {
                p.add(cb.equal(root.get("estado"), f.estado()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}

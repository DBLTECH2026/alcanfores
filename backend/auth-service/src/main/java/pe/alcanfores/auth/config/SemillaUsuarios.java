package pe.alcanfores.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.auth.repositorio.AdministradorRepositorio;

import java.util.List;

/**
 * Crea los 3 usuarios iniciales del SPEC §5.1 solo si la tabla está vacía. Las claves vienen de variables de entorno
 * ({@code SEED_CLAVE_ADMIN}, {@code SEED_CLAVE_RECEPCION}, {@code SEED_CLAVE_DOCENTE}) y nunca se escriben en SQL.
 */
@Component
public class SemillaUsuarios implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SemillaUsuarios.class);

    private final AdministradorRepositorio administradores;
    private final PasswordEncoder codificador;
    private final AuthPropiedades propiedades;

    public SemillaUsuarios(AdministradorRepositorio administradores, PasswordEncoder codificador,
                           AuthPropiedades propiedades) {
        this.administradores = administradores;
        this.codificador = codificador;
        this.propiedades = propiedades;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!propiedades.semilla().habilitada()) {
            return;
        }
        if (administradores.count() > 0) {
            log.info("Semilla de usuarios omitida: la tabla administrador ya tiene datos");
            return;
        }
        AuthPropiedades.Semilla s = propiedades.semilla();
        List<Administrador> usuarios = List.of(
                nuevo("Rosa", "Huamán", "rhuaman", "rhuaman@alcanfores.pe", Rol.ADMIN, s.claveAdmin()),
                nuevo("Marco", "Torres", "mtorres", "mtorres@alcanfores.pe", Rol.RECEPCION, s.claveRecepcion()),
                nuevo("Docente", "UTP", "docente", "docente@alcanfores.pe", Rol.ADMIN, s.claveDocente()));
        administradores.saveAll(usuarios);
        log.info("Semilla de usuarios creada: rhuaman (ADMIN), mtorres (RECEPCION), docente (ADMIN)");
    }

    private Administrador nuevo(String nombres, String apellidos, String usuario, String correo, Rol rol, String clave) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("Falta la clave inicial del usuario " + usuario + " (variables SEED_CLAVE_*).");
        }
        Administrador a = new Administrador();
        a.setNombres(nombres);
        a.setApellidos(apellidos);
        a.setUsuario(usuario);
        a.setCorreo(correo);
        a.setRol(rol);
        a.setEstado(EstadoUsuario.ACTIVO);
        a.setContrasenaHash(codificador.encode(clave));
        return a;
    }
}

package pe.alcanfores.auth.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.auth.config.AuthPropiedades;
import pe.alcanfores.auth.dto.CambiarContrasenaSolicitud;
import pe.alcanfores.auth.dto.EditarMisDatosSolicitud;
import pe.alcanfores.auth.dto.LoginRespuesta;
import pe.alcanfores.auth.dto.LoginSolicitud;
import pe.alcanfores.auth.dto.UsuarioRespuesta;
import pe.alcanfores.auth.mapeador.UsuarioMapeador;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.repositorio.AdministradorRepositorio;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class AutenticacionServicioImpl implements AutenticacionServicio {

    static final String ACCION_LOGIN_EXITOSO = "LOGIN_EXITOSO";
    static final String ACCION_LOGIN_FALLIDO = "LOGIN_FALLIDO";
    static final String ACCION_LOGIN_BLOQUEADO = "LOGIN_BLOQUEADO";
    static final String ACCION_LOGIN_INACTIVO = "LOGIN_INACTIVO";
    static final String ACCION_CAMBIAR_CONTRASENA = "CAMBIAR_CONTRASENA";
    static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";

    private static final Logger log = LoggerFactory.getLogger(AutenticacionServicioImpl.class);

    private final AdministradorRepositorio administradores;
    private final PasswordEncoder codificador;
    private final JwtServicio jwt;
    private final BitacoraServicio bitacora;
    private final RegistroIntentosAnonimos anonimos;
    private final AuthPropiedades propiedades;
    /** Hash señuelo: se compara aunque el usuario no exista para que el tiempo de respuesta no lo delate. */
    private final String hashSenuelo;

    public AutenticacionServicioImpl(AdministradorRepositorio administradores, PasswordEncoder codificador, JwtServicio jwt,
                                     BitacoraServicio bitacora, RegistroIntentosAnonimos anonimos,
                                     AuthPropiedades propiedades) {
        this.administradores = administradores;
        this.codificador = codificador;
        this.jwt = jwt;
        this.bitacora = bitacora;
        this.anonimos = anonimos;
        this.propiedades = propiedades;
        this.hashSenuelo = codificador.encode("senuelo-para-igualar-tiempos");
    }

    @Override
    @Transactional(noRollbackFor = NegocioExcepcion.class)
    public LoginRespuesta iniciarSesion(LoginSolicitud s) {
        String usuario = s.usuario().trim().toLowerCase(Locale.ROOT);
        LocalDateTime ahora = Tiempo.ahora();
        Optional<Administrador> encontrado = administradores.buscarParaLogin(usuario);
        if (encontrado.isEmpty()) {
            throw falloConUsuarioInexistente(usuario, s.contrasena(), ahora);
        }
        Administrador a = encontrado.get();
        if (a.estaBloqueado(ahora)) {
            bitacora.registrarComo(a.getId(), a.getUsuario(), a.getRol().name(), ACCION_LOGIN_BLOQUEADO, "administrador",
                    a.getId(), "Intento de ingreso con la cuenta bloqueada");
            throw bloqueado(a.getBloqueadoHasta(), ahora);
        }
        if (!coincide(s.contrasena(), a.getContrasenaHash())) {
            boolean seBloqueo = a.registrarIntentoFallido(ahora, propiedades.intentosMaximos(), propiedades.minutosBloqueo());
            administradores.save(a);
            bitacora.registrarComo(a.getId(), a.getUsuario(), a.getRol().name(), ACCION_LOGIN_FALLIDO, "administrador",
                    a.getId(), seBloqueo
                            ? "Contraseña incorrecta: cuenta bloqueada " + propiedades.minutosBloqueo() + " minutos"
                            : "Contraseña incorrecta (intento " + a.getIntentosFallidos() + " de " + propiedades.intentosMaximos() + ")");
            if (seBloqueo) {
                log.warn("Cuenta {} bloqueada por {} intentos fallidos", a.getUsuario(), propiedades.intentosMaximos());
                throw bloqueado(a.getBloqueadoHasta(), ahora);
            }
            throw credencialesInvalidas();
        }
        if (!a.estaActivo()) {
            bitacora.registrarComo(a.getId(), a.getUsuario(), a.getRol().name(), ACCION_LOGIN_INACTIVO, "administrador",
                    a.getId(), "Ingreso rechazado: usuario desactivado");
            throw new NegocioExcepcion(403, "USUARIO_INACTIVO",
                    "Tu usuario está desactivado. Pide a un administrador que lo vuelva a activar.");
        }
        a.registrarAccesoExitoso(ahora);
        administradores.save(a);
        bitacora.registrarComo(a.getId(), a.getUsuario(), a.getRol().name(), ACCION_LOGIN_EXITOSO, "administrador",
                a.getId(), "Inicio de sesión");
        String token = jwt.generar(UsuarioMapeador.aToken(a));
        LocalDateTime expiraEn = LocalDateTime.ofInstant(jwt.expiraEn(), Tiempo.ZONA).withNano(0);
        return LoginRespuesta.bearer(token, expiraEn, UsuarioMapeador.aSesion(a));
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioRespuesta usuarioActual(UsuarioToken token) {
        return UsuarioMapeador.aRespuesta(vigente(token));
    }

    @Override
    @Transactional
    public void cambiarContrasena(UsuarioToken token, CambiarContrasenaSolicitud s) {
        Administrador a = vigente(token);
        if (!coincide(s.actual(), a.getContrasenaHash())) {
            throw NegocioExcepcion.validacion("actual", "La contraseña actual no es correcta.");
        }
        if (s.nueva().equals(s.actual())) {
            throw NegocioExcepcion.validacion("nueva", "La contraseña nueva debe ser distinta de la actual.");
        }
        a.setContrasenaHash(codificador.encode(s.nueva()));
        administradores.save(a);
        bitacora.registrar(ACCION_CAMBIAR_CONTRASENA, "administrador", a.getId(), "Cambió su propia contraseña");
    }

    @Override
    @Transactional
    public UsuarioRespuesta editarMisDatos(UsuarioToken token, EditarMisDatosSolicitud s) {
        Administrador a = vigente(token);
        String correo = Texto.correo(s.correo());
        if (administradores.existsByCorreoAndIdNot(correo, a.getId())) {
            throw NegocioExcepcion.conflicto("CORREO_DUPLICADO", "Ese correo ya está registrado en otro usuario.");
        }
        String telefono = Texto.normalizarCelular(s.telefono());
        List<String> cambios = new ArrayList<>();
        if (!Objects.equals(a.getCorreo(), correo)) {
            cambios.add("correo: " + a.getCorreo() + " -> " + correo);
        }
        if (!Objects.equals(a.getTelefono(), telefono)) {
            cambios.add("telefono: " + (a.getTelefono() == null ? "-" : a.getTelefono()) + " -> "
                    + (telefono == null ? "-" : telefono));
        }
        a.setCorreo(correo);
        a.setTelefono(telefono);
        administradores.save(a);
        if (!cambios.isEmpty()) {
            bitacora.registrar("EDITAR_MIS_DATOS", "administrador", a.getId(), String.join("; ", cambios));
        }
        return UsuarioMapeador.aRespuesta(a);
    }

    /** El token puede seguir vigente aunque el usuario haya sido desactivado o eliminado: se revisa en la BD. */
    private Administrador vigente(UsuarioToken token) {
        return administradores.findById(token.id())
                .filter(Administrador::estaActivo)
                .orElseThrow(() -> new NegocioExcepcion(401, "NO_AUTENTICADO",
                        "Tu usuario ya no está activo. Vuelve a iniciar sesión."));
    }

    private NegocioExcepcion falloConUsuarioInexistente(String usuario, String contrasena, LocalDateTime ahora) {
        Optional<LocalDateTime> yaBloqueado = anonimos.bloqueadoHasta(usuario, ahora);
        if (yaBloqueado.isPresent()) {
            return bloqueado(yaBloqueado.get(), ahora);
        }
        coincide(contrasena, hashSenuelo);
        Optional<LocalDateTime> bloqueo = anonimos.registrarFallo(usuario, ahora);
        bitacora.registrarComo(null, usuario, null, ACCION_LOGIN_FALLIDO, "administrador", null,
                "Intento de ingreso con un usuario que no existe");
        return bloqueo.map(hasta -> bloqueado(hasta, ahora)).orElseGet(AutenticacionServicioImpl::credencialesInvalidas);
    }

    private boolean coincide(String contrasena, String hash) {
        try {
            return codificador.matches(contrasena, hash);
        } catch (IllegalArgumentException e) {
            // BCrypt rechaza claves de más de 72 bytes: simplemente no coinciden.
            return false;
        }
    }

    static NegocioExcepcion credencialesInvalidas() {
        return new NegocioExcepcion(401, "CREDENCIALES_INVALIDAS", MENSAJE_CREDENCIALES);
    }

    static NegocioExcepcion bloqueado(LocalDateTime hasta, LocalDateTime ahora) {
        long segundos = Math.max(Duration.between(ahora, hasta).getSeconds(), 0);
        long minutos = Math.max(1, (segundos + 59) / 60);
        return new NegocioExcepcion(423, "USUARIO_BLOQUEADO", "Por seguridad, la cuenta quedó bloqueada tras varios intentos "
                + "fallidos. Intenta de nuevo en " + minutos + (minutos == 1 ? " minuto." : " minutos."));
    }
}

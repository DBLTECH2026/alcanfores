package pe.alcanfores.auth.servicio;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.auth.config.AuthPropiedades;
import pe.alcanfores.auth.dto.CambiarContrasenaSolicitud;
import pe.alcanfores.auth.dto.ClienteSesion;
import pe.alcanfores.auth.dto.EditarClienteSolicitud;
import pe.alcanfores.auth.dto.LoginClienteRespuesta;
import pe.alcanfores.auth.dto.LoginClienteSolicitud;
import pe.alcanfores.auth.dto.RegistroClienteSolicitud;
import pe.alcanfores.auth.mapeador.CuentaClienteMapeador;
import pe.alcanfores.auth.modelo.CuentaCliente;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.repositorio.CuentaClienteRepositorio;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CuentaClienteServicioImpl implements CuentaClienteServicio {

    static final String ENTIDAD = "cuenta_cliente";
    static final String MENSAJE_CREDENCIALES = "DNI, correo o contraseña incorrectos.";
    static final String MENSAJE_DNI_REGISTRADO = "Ese DNI ya tiene una cuenta. Inicia sesión con tu DNI o tu correo.";
    static final String MENSAJE_CORREO_REGISTRADO = "Ese correo ya está registrado en otra cuenta.";
    /** Prefijo para que los intentos con cuentas inexistentes no se mezclen con los de administradores. */
    private static final String PREFIJO_ANONIMO = "cliente:";

    private final CuentaClienteRepositorio cuentas;
    private final PasswordEncoder codificador;
    private final JwtServicio jwt;
    private final BitacoraServicio bitacora;
    private final RegistroIntentosAnonimos anonimos;
    private final AuthPropiedades propiedades;
    /** Hash señuelo: se compara aunque la cuenta no exista para que el tiempo de respuesta no lo delate. */
    private final String hashSenuelo;

    public CuentaClienteServicioImpl(CuentaClienteRepositorio cuentas, PasswordEncoder codificador, JwtServicio jwt,
                                     BitacoraServicio bitacora, RegistroIntentosAnonimos anonimos,
                                     AuthPropiedades propiedades) {
        this.cuentas = cuentas;
        this.codificador = codificador;
        this.jwt = jwt;
        this.bitacora = bitacora;
        this.anonimos = anonimos;
        this.propiedades = propiedades;
        this.hashSenuelo = codificador.encode("senuelo-para-igualar-tiempos");
    }

    @Override
    @Transactional
    public LoginClienteRespuesta registrar(RegistroClienteSolicitud s) {
        String dni = s.dni().trim();
        String correo = Texto.correo(s.correo());
        if (cuentas.existsByDni(dni)) {
            throw dniRegistrado();
        }
        if (cuentas.existsByCorreo(correo)) {
            throw correoRegistrado();
        }
        CuentaCliente c = new CuentaCliente();
        c.setDni(dni);
        c.setNombres(Texto.titulo(s.nombres()));
        c.setApellidos(Texto.titulo(s.apellidos()));
        c.setCorreo(correo);
        c.setTelefono(Texto.normalizarCelular(s.telefono()));
        c.setEstado(EstadoUsuario.ACTIVO);
        c.setContrasenaHash(codificador.encode(s.contrasena()));
        c.registrarAccesoExitoso(Tiempo.ahora());
        try {
            cuentas.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultáneos con el mismo DNI o correo: el índice único decide.
            throw cuentas.existsByCorreo(correo) && !cuentas.existsByDni(dni) ? correoRegistrado() : dniRegistrado();
        }
        bitacora.registrarComo(c.getId(), c.getCorreo(), UsuarioToken.CLIENTE, "REGISTRAR_CUENTA", ENTIDAD, c.getId(),
                "Creó su cuenta de cliente en el portal");
        return sesion(c);
    }

    @Override
    @Transactional(noRollbackFor = NegocioExcepcion.class)
    public LoginClienteRespuesta iniciarSesion(LoginClienteSolicitud s) {
        String usuario = s.usuario().trim().toLowerCase(Locale.ROOT);
        LocalDateTime ahora = Tiempo.ahora();
        Optional<CuentaCliente> encontrada = cuentas.buscarParaLogin(usuario);
        if (encontrada.isEmpty()) {
            throw falloConCuentaInexistente(usuario, s.contrasena(), ahora);
        }
        CuentaCliente c = encontrada.get();
        if (c.estaBloqueada(ahora)) {
            throw AutenticacionServicioImpl.bloqueado(c.getBloqueadoHasta(), ahora);
        }
        if (!coincide(s.contrasena(), c.getContrasenaHash())) {
            boolean seBloqueo = c.registrarIntentoFallido(ahora, propiedades.intentosMaximos(), propiedades.minutosBloqueo());
            cuentas.save(c);
            if (seBloqueo) {
                bitacora.registrarComo(c.getId(), c.getCorreo(), UsuarioToken.CLIENTE, "LOGIN_CLIENTE_BLOQUEADO", ENTIDAD,
                        c.getId(), "Cuenta bloqueada " + propiedades.minutosBloqueo() + " minutos por intentos fallidos");
                throw AutenticacionServicioImpl.bloqueado(c.getBloqueadoHasta(), ahora);
            }
            throw credencialesInvalidas();
        }
        if (!c.estaActiva()) {
            throw new NegocioExcepcion(403, "USUARIO_INACTIVO",
                    "Tu cuenta está desactivada. Escríbenos por WhatsApp para volver a activarla.");
        }
        c.registrarAccesoExitoso(ahora);
        cuentas.save(c);
        return sesion(c);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteSesion actual(UsuarioToken token) {
        return CuentaClienteMapeador.aSesion(vigente(token));
    }

    @Override
    @Transactional
    public ClienteSesion editar(UsuarioToken token, EditarClienteSolicitud s) {
        CuentaCliente c = vigente(token);
        String correo = Texto.correo(s.correo());
        if (cuentas.existsByCorreoAndIdNot(correo, c.getId())) {
            throw correoRegistrado();
        }
        c.setNombres(Texto.titulo(s.nombres()));
        c.setApellidos(Texto.titulo(s.apellidos()));
        c.setCorreo(correo);
        c.setTelefono(Texto.normalizarCelular(s.telefono()));
        cuentas.save(c);
        bitacora.registrarComo(c.getId(), c.getCorreo(), UsuarioToken.CLIENTE, "EDITAR_CUENTA", ENTIDAD, c.getId(),
                "Actualizó sus datos de contacto");
        return CuentaClienteMapeador.aSesion(c);
    }

    @Override
    @Transactional
    public void cambiarContrasena(UsuarioToken token, CambiarContrasenaSolicitud s) {
        CuentaCliente c = vigente(token);
        if (!coincide(s.actual(), c.getContrasenaHash())) {
            throw NegocioExcepcion.validacion("actual", "La contraseña actual no es correcta.");
        }
        if (s.nueva().equals(s.actual())) {
            throw NegocioExcepcion.validacion("nueva", "La contraseña nueva debe ser distinta de la actual.");
        }
        c.setContrasenaHash(codificador.encode(s.nueva()));
        cuentas.save(c);
        bitacora.registrarComo(c.getId(), c.getCorreo(), UsuarioToken.CLIENTE, "CAMBIAR_CONTRASENA", ENTIDAD, c.getId(),
                "Cambió la contraseña de su cuenta de cliente");
    }

    private LoginClienteRespuesta sesion(CuentaCliente c) {
        String token = jwt.generar(CuentaClienteMapeador.aToken(c));
        LocalDateTime expiraEn = LocalDateTime.ofInstant(jwt.expiraEn(), Tiempo.ZONA).withNano(0);
        return LoginClienteRespuesta.bearer(token, expiraEn, CuentaClienteMapeador.aSesion(c));
    }

    /** El token puede seguir vigente aunque la cuenta se haya desactivado: se revisa en la BD. */
    private CuentaCliente vigente(UsuarioToken token) {
        return cuentas.findById(token.id())
                .filter(CuentaCliente::estaActiva)
                .orElseThrow(() -> new NegocioExcepcion(401, "NO_AUTENTICADO",
                        "Tu cuenta ya no está activa. Vuelve a iniciar sesión."));
    }

    private NegocioExcepcion falloConCuentaInexistente(String usuario, String contrasena, LocalDateTime ahora) {
        String clave = PREFIJO_ANONIMO + usuario;
        Optional<LocalDateTime> yaBloqueado = anonimos.bloqueadoHasta(clave, ahora);
        if (yaBloqueado.isPresent()) {
            return AutenticacionServicioImpl.bloqueado(yaBloqueado.get(), ahora);
        }
        coincide(contrasena, hashSenuelo);
        return anonimos.registrarFallo(clave, ahora)
                .map(hasta -> AutenticacionServicioImpl.bloqueado(hasta, ahora))
                .orElseGet(CuentaClienteServicioImpl::credencialesInvalidas);
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

    static NegocioExcepcion dniRegistrado() {
        return new NegocioExcepcion(409, "DNI_REGISTRADO", MENSAJE_DNI_REGISTRADO,
                List.of(new ErrorCampo("dni", MENSAJE_DNI_REGISTRADO)));
    }

    static NegocioExcepcion correoRegistrado() {
        return new NegocioExcepcion(409, "CORREO_REGISTRADO", MENSAJE_CORREO_REGISTRADO,
                List.of(new ErrorCampo("correo", MENSAJE_CORREO_REGISTRADO)));
    }
}

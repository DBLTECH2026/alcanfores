package pe.alcanfores.auth.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.auth.cliente.NotificacionesCliente;
import pe.alcanfores.auth.dto.RecuperarDtos.CanalDisponible;
import pe.alcanfores.auth.dto.RecuperarDtos.EnviarSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.EnvioRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.OpcionesRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.OpcionesSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.RestablecerSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.VerificacionRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.VerificarSolicitud;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.CodigoRecuperacion;
import pe.alcanfores.auth.modelo.CodigoRecuperacion.Canal;
import pe.alcanfores.auth.modelo.CodigoRecuperacion.Estado;
import pe.alcanfores.auth.modelo.CodigoRecuperacion.TipoCuenta;
import pe.alcanfores.auth.modelo.CuentaCliente;
import pe.alcanfores.auth.repositorio.AdministradorRepositorio;
import pe.alcanfores.auth.repositorio.CodigoRecuperacionRepositorio;
import pe.alcanfores.auth.repositorio.CuentaClienteRepositorio;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RecuperacionServicioImpl implements RecuperacionServicio {

    static final int MINUTOS_VIGENCIA = 10;
    static final int MAX_INTENTOS = 5;
    static final int MAX_ENVIOS = 3;
    static final int VENTANA_ENVIOS_MIN = 15;
    static final int SEGUNDOS_REENVIO = 60;
    static final String MENSAJE_NO_ENCONTRADA = "No encontramos una cuenta con esos datos.";
    static final String MENSAJE_VENCIDO = "El código venció o ya no es válido. Pide uno nuevo.";
    static final String MENSAJE_TOKEN = "El permiso para cambiar la contraseña venció o ya se usó. Vuelve a pedir un código.";
    private static final Logger log = LoggerFactory.getLogger(RecuperacionServicioImpl.class);

    /** Cuenta de cliente o de usuario del panel vista de la misma forma. */
    record Cuenta(TipoCuenta tipo, Long id, String nombre, String correo, String telefono, String actor, String rol) {

        String entidad() {
            return tipo == TipoCuenta.CLIENTE ? "cuenta_cliente" : "administrador";
        }

        Optional<String> destino(Canal canal) {
            return Optional.ofNullable(canal == Canal.CORREO ? correo : telefono).filter(d -> !d.isBlank());
        }
    }

    private final CuentaClienteRepositorio cuentas;
    private final AdministradorRepositorio administradores;
    private final CodigoRecuperacionRepositorio codigos;
    private final NotificacionesCliente notificaciones;
    private final BitacoraServicio bitacora;
    private final PasswordEncoder codificador;
    private final TransactionTemplate tx;
    private final SecretKeySpec llaveHmac;
    private final boolean devolverCodigo;
    private final SecureRandom azar = new SecureRandom();

    public RecuperacionServicioImpl(CuentaClienteRepositorio cuentas, AdministradorRepositorio administradores,
                                    CodigoRecuperacionRepositorio codigos, NotificacionesCliente notificaciones,
                                    BitacoraServicio bitacora, PasswordEncoder codificador, TransactionTemplate tx,
                                    AlcanforesPropiedades alcanfores,
                                    @Value("${alcanfores.auth.recuperar.devolver-codigo:false}") String devolverCodigo) {
        this.cuentas = cuentas;
        this.administradores = administradores;
        this.codigos = codigos;
        this.notificaciones = notificaciones;
        this.bitacora = bitacora;
        this.codificador = codificador;
        this.tx = tx;
        this.llaveHmac = new SecretKeySpec(alcanfores.getJwt().getSecreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.devolverCodigo = "true".equalsIgnoreCase(devolverCodigo == null ? "" : devolverCodigo.trim());
        if (this.devolverCodigo) {
            log.warn("RECUPERAR_DEVOLVER_CODIGO=true: los códigos de recuperación se devuelven en la respuesta. "
                    + "Solo para pruebas locales; NUNCA en producción.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OpcionesRespuesta opciones(OpcionesSolicitud s) {
        Cuenta c = buscar(s.tipoCuenta(), s.usuario());
        List<CanalDisponible> canales = new ArrayList<>(2);
        for (Canal canal : Canal.values()) {
            c.destino(canal).ifPresent(d -> canales.add(new CanalDisponible(canal, enmascarar(canal, d))));
        }
        return new OpcionesRespuesta(canales);
    }

    @Override
    public EnvioRespuesta enviar(EnviarSolicitud s) {
        Cuenta c = tx.execute(estado -> buscar(s.tipoCuenta(), s.usuario()));
        String destino = c.destino(s.canal()).orElseThrow(() -> NegocioExcepcion.regla("CANAL_NO_DISPONIBLE",
                "Esta cuenta no tiene un celular registrado. Recibe el código por correo."));
        String codigo = String.format("%06d", azar.nextInt(1_000_000));
        LocalDateTime ahora = Tiempo.ahora();
        CodigoRecuperacion nuevo = tx.execute(estado -> registrarCodigo(c, s.canal(), destino, codigo, ahora));
        NotificacionesCliente.Resultado r = notificaciones.enviar(new NotificacionesCliente.Solicitud(s.canal(), destino,
                c.nombre(), codigo, NotificacionesCliente.RECUPERAR));
        if (r.fallido()) {
            throw NegocioExcepcion.noDisponible("ENVIO_FALLIDO", "No pudimos enviar el código por "
                    + (s.canal() == Canal.CORREO ? "correo" : "WhatsApp") + ". Prueba con el otro medio o intenta en unos minutos.");
        }
        bitacora.registrarComo(c.id(), c.actor(), c.rol(), "ENVIAR_CODIGO_RECUPERACION", c.entidad(), c.id(),
                "Código para cambiar la contraseña por " + s.canal() + " a " + nuevo.getDestino()
                        + (r.simulado() ? " (canal simulado)" : ""));
        return new EnvioRespuesta(s.canal(), nuevo.getDestino(), nuevo.getExpiraEn(), ahora.plusSeconds(SEGUNDOS_REENVIO),
                SEGUNDOS_REENVIO, r.simulado(), devolverCodigo ? codigo : null);
    }

    @Override
    @Transactional(noRollbackFor = NegocioExcepcion.class)
    public VerificacionRespuesta verificar(VerificarSolicitud s) {
        Cuenta c = buscar(s.tipoCuenta(), s.usuario());
        LocalDateTime ahora = Tiempo.ahora();
        CodigoRecuperacion cod = codigos.ultimoParaVerificar(c.tipo(), c.id())
                .filter(x -> x.getEstado() == Estado.PENDIENTE)
                .orElseThrow(() -> NegocioExcepcion.regla("CODIGO_VENCIDO", MENSAJE_VENCIDO));
        if (cod.vencido(ahora)) {
            cod.setEstado(Estado.ANULADO);
            throw NegocioExcepcion.regla("CODIGO_VENCIDO", MENSAJE_VENCIDO);
        }
        if (!MessageDigest.isEqual(hashCodigo(c, s.codigo()).getBytes(StandardCharsets.US_ASCII),
                cod.getCodigoHash().getBytes(StandardCharsets.US_ASCII))) {
            cod.setIntentos(cod.getIntentos() + 1);
            int quedan = MAX_INTENTOS - cod.getIntentos();
            if (quedan <= 0) {
                cod.setEstado(Estado.ANULADO);
                throw NegocioExcepcion.regla("DEMASIADOS_INTENTOS",
                        "Ingresaste mal el código " + MAX_INTENTOS + " veces. Pide uno nuevo.");
            }
            String m = "El código no es correcto. Te " + (quedan == 1 ? "queda 1 intento." : "quedan " + quedan + " intentos.");
            throw new NegocioExcepcion(422, "CODIGO_INCORRECTO", m, List.of(new ErrorCampo("codigo", m)));
        }
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        cod.setEstado(Estado.VERIFICADO);
        cod.setTokenHash(sha256(token));
        cod.setTokenExpiraEn(ahora.plusMinutes(MINUTOS_VIGENCIA));
        return new VerificacionRespuesta(token, cod.getTokenExpiraEn());
    }

    @Override
    public void restablecer(RestablecerSolicitud s) {
        Aviso aviso = tx.execute(estado -> cambiarContrasena(s));
        try {
            notificaciones.enviar(new NotificacionesCliente.Solicitud(aviso.canal(), aviso.destino(), aviso.nombre(), null,
                    NotificacionesCliente.CAMBIADA));
        } catch (NegocioExcepcion e) {
            log.warn("La contraseña se cambió pero no se pudo avisar por {}: {}", aviso.canal(), e.getMessage());
        }
    }

    private record Aviso(Canal canal, String destino, String nombre) {
    }

    private Aviso cambiarContrasena(RestablecerSolicitud s) {
        LocalDateTime ahora = Tiempo.ahora();
        CodigoRecuperacion cod = codigos.findByTokenHash(sha256(s.tokenRestablecer()))
                .filter(x -> x.getEstado() == Estado.VERIFICADO && x.getTokenExpiraEn().isAfter(ahora))
                .orElseThrow(() -> NegocioExcepcion.regla("TOKEN_INVALIDO", MENSAJE_TOKEN));
        String hash = codificador.encode(s.nueva());
        Cuenta c;
        if (cod.getTipoCuenta() == TipoCuenta.CLIENTE) {
            CuentaCliente cc = cuentas.findById(cod.getIdCuenta()).filter(CuentaCliente::estaActiva)
                    .orElseThrow(() -> NegocioExcepcion.regla("TOKEN_INVALIDO", MENSAJE_TOKEN));
            cc.setContrasenaHash(hash);
            cc.setIntentosFallidos(0);
            cc.setBloqueadoHasta(null);
            c = deCliente(cc);
        } else {
            Administrador a = administradores.findById(cod.getIdCuenta()).filter(Administrador::estaActivo)
                    .orElseThrow(() -> NegocioExcepcion.regla("TOKEN_INVALIDO", MENSAJE_TOKEN));
            a.setContrasenaHash(hash);
            a.desbloquear();
            c = deAdmin(a);
        }
        cod.setEstado(Estado.USADO);
        cod.setUsadoEn(ahora);
        codigos.saveAndFlush(cod);
        codigos.anularVigentes(c.tipo(), c.id());
        bitacora.registrarComo(c.id(), c.actor(), c.rol(), "RECUPERAR_CONTRASENA", c.entidad(), c.id(),
                "Cambió su contraseña con un código enviado por " + cod.getCanal() + " (cuenta desbloqueada)");
        return new Aviso(cod.getCanal(), c.destino(cod.getCanal()).orElse(cod.getDestino()), c.nombre());
    }

    private CodigoRecuperacion registrarCodigo(Cuenta c, Canal canal, String destino, String codigo, LocalDateTime ahora) {
        List<CodigoRecuperacion> recientes = codigos.findByTipoCuentaAndIdCuentaAndCreadoEnAfterOrderByCreadoEnDesc(
                c.tipo(), c.id(), ahora.minusMinutes(VENTANA_ENVIOS_MIN));
        if (!recientes.isEmpty()) {
            long espera = Duration.between(ahora, recientes.get(0).getCreadoEn().plusSeconds(SEGUNDOS_REENVIO)).toSeconds();
            if (espera > 0) {
                throw new NegocioExcepcion(429, "DEMASIADOS_ENVIOS",
                        "Espera " + espera + (espera == 1 ? " segundo" : " segundos") + " para pedir otro código.");
            }
        }
        if (recientes.size() >= MAX_ENVIOS) {
            LocalDateTime libre = recientes.get(MAX_ENVIOS - 1).getCreadoEn().plusMinutes(VENTANA_ENVIOS_MIN);
            long minutos = Math.max(1, (Duration.between(ahora, libre).toSeconds() + 59) / 60);
            throw new NegocioExcepcion(429, "DEMASIADOS_ENVIOS", "Ya te enviamos " + MAX_ENVIOS + " códigos en los últimos "
                    + VENTANA_ENVIOS_MIN + " minutos. Intenta de nuevo en " + minutos + (minutos == 1 ? " minuto." : " minutos."));
        }
        codigos.anularVigentes(c.tipo(), c.id());
        CodigoRecuperacion n = new CodigoRecuperacion();
        n.setTipoCuenta(c.tipo());
        n.setIdCuenta(c.id());
        n.setCanal(canal);
        n.setDestino(enmascarar(canal, destino));
        n.setCodigoHash(hashCodigo(c, codigo));
        n.setExpiraEn(ahora.plusMinutes(MINUTOS_VIGENCIA));
        n.setCreadoEn(ahora);
        n.setIp(IpCliente.actual());
        return codigos.save(n);
    }

    /** Cuenta ACTIVA por DNI o correo (cliente) o por usuario o correo (panel); si no, 404 con el mismo mensaje. */
    private Cuenta buscar(TipoCuenta tipo, String usuario) {
        String u = usuario.trim().toLowerCase(Locale.ROOT);
        Optional<Cuenta> c = tipo == TipoCuenta.CLIENTE
                ? cuentas.findFirstByDniOrCorreo(u, u).filter(CuentaCliente::estaActiva).map(RecuperacionServicioImpl::deCliente)
                : administradores.findFirstByUsuarioOrCorreo(u, u).filter(Administrador::estaActivo)
                .map(RecuperacionServicioImpl::deAdmin);
        return c.orElseThrow(() -> new NegocioExcepcion(404, "CUENTA_NO_ENCONTRADA", MENSAJE_NO_ENCONTRADA));
    }

    private static Cuenta deCliente(CuentaCliente c) {
        return new Cuenta(TipoCuenta.CLIENTE, c.getId(), c.nombreCompleto(), c.getCorreo(), c.getTelefono(), c.getCorreo(),
                UsuarioToken.CLIENTE);
    }

    private static Cuenta deAdmin(Administrador a) {
        return new Cuenta(TipoCuenta.ADMIN, a.getId(), a.nombreCompleto(), a.getCorreo(), a.getTelefono(), a.getUsuario(),
                a.getRol().name());
    }

    /** «lucia@gmail.com» → «l****@gmail.com»; «987654254» → «*** *** 254». */
    static String enmascarar(Canal canal, String destino) {
        if (canal == Canal.WHATSAPP) {
            return "*** *** " + destino.substring(Math.max(0, destino.length() - 3));
        }
        int arroba = destino.indexOf('@');
        return arroba <= 0 ? "****" : destino.charAt(0) + "****" + destino.substring(arroba);
    }

    /** HMAC-SHA256 (llave = JWT_SECRETO) de la cuenta + el código: sin el secreto no se pueden probar los 10^6 códigos. */
    private String hashCodigo(Cuenta c, String codigo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(llaveHmac);
            return HexFormat.of().formatHex(mac.doFinal((c.tipo() + ":" + c.id() + ":" + codigo).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no está disponible en esta JVM", e);
        }
    }

    static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }
}

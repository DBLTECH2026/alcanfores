package pe.alcanfores.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.alcanfores.auth.mapeador.CuentaClienteMapeador;
import pe.alcanfores.auth.mapeador.UsuarioMapeador;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.CuentaCliente;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.auth.repositorio.AdministradorRepositorio;
import pe.alcanfores.auth.repositorio.BitacoraRepositorio;
import pe.alcanfores.auth.repositorio.CuentaClienteRepositorio;
import pe.alcanfores.auth.repositorio.EventoProcesadoRepositorio;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.util.Tiempo;

/** Base de las pruebas de integración: contexto completo contra la BD {@code alcanfores_auth_test} (Flyway). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PruebaIntegracion {

    protected static final String CLAVE = "Alcanfores2026";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected AdministradorRepositorio administradores;
    @Autowired
    protected CuentaClienteRepositorio cuentas;
    @Autowired
    protected BitacoraRepositorio bitacoras;
    @Autowired
    protected EventoProcesadoRepositorio eventosProcesados;
    @Autowired
    protected PasswordEncoder codificador;
    @Autowired
    protected JwtServicio jwt;
    @Autowired
    protected AlcanforesPropiedades propiedades;

    @MockitoBean
    protected RabbitTemplate rabbit;

    @BeforeEach
    void limpiarBase() {
        Tiempo.restablecer();
        bitacoras.deleteAllInBatch();
        eventosProcesados.deleteAllInBatch();
        administradores.deleteAllInBatch();
        cuentas.deleteAllInBatch();
    }

    @AfterEach
    void restablecerReloj() {
        Tiempo.restablecer();
    }

    protected Administrador crearUsuario(String usuario, Rol rol) {
        return crearUsuario(usuario, rol, CLAVE);
    }

    protected Administrador crearUsuario(String usuario, Rol rol, String clave) {
        Administrador a = new Administrador();
        a.setNombres("Nombre " + usuario.replaceAll("[^a-z]", ""));
        a.setApellidos("Apellido");
        a.setUsuario(usuario);
        a.setCorreo(usuario + "@alcanfores.pe");
        a.setRol(rol);
        a.setEstado(EstadoUsuario.ACTIVO);
        a.setContrasenaHash(codificador.encode(clave));
        return administradores.save(a);
    }

    protected String bearer(Administrador a) {
        return "Bearer " + jwt.generar(UsuarioMapeador.aToken(a));
    }

    protected CuentaCliente crearCuenta(String dni, String correo, String clave) {
        CuentaCliente c = new CuentaCliente();
        c.setDni(dni);
        c.setNombres("Lucía");
        c.setApellidos("Mendoza Paredes");
        c.setCorreo(correo);
        c.setTelefono("987654321");
        c.setEstado(EstadoUsuario.ACTIVO);
        c.setContrasenaHash(codificador.encode(clave));
        return cuentas.save(c);
    }

    protected String bearer(CuentaCliente c) {
        return "Bearer " + jwt.generar(CuentaClienteMapeador.aToken(c));
    }

    protected String bearerVencido(Administrador a) {
        return "Bearer " + new JwtServicio(propiedades.getJwt().getSecreto(), -1).generar(UsuarioMapeador.aToken(a));
    }

    protected MockHttpServletRequestBuilder conJson(MockHttpServletRequestBuilder req, Object cuerpo) throws Exception {
        return req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
    }
}

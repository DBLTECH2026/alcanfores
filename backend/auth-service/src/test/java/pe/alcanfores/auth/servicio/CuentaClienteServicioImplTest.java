package pe.alcanfores.auth.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pe.alcanfores.auth.config.AuthPropiedades;
import pe.alcanfores.auth.dto.LoginClienteSolicitud;
import pe.alcanfores.auth.dto.RegistroClienteSolicitud;
import pe.alcanfores.auth.repositorio.CuentaClienteRepositorio;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.JwtServicio;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Casos que no se alcanzan por HTTP: dos registros simultáneos que chocan en el índice único. */
class CuentaClienteServicioImplTest {

    private final CuentaClienteRepositorio cuentas = mock(CuentaClienteRepositorio.class);
    private CuentaClienteServicioImpl servicio;

    @BeforeEach
    void preparar() {
        AuthPropiedades p = new AuthPropiedades(4, 5, 15, null);
        servicio = new CuentaClienteServicioImpl(cuentas, new BCryptPasswordEncoder(4),
                new JwtServicio("clave-de-prueba-de-al-menos-32-bytes-123456", 8), mock(BitacoraServicio.class),
                new RegistroIntentosAnonimos(p), p);
    }

    private static RegistroClienteSolicitud solicitud() {
        return new RegistroClienteSolicitud("70123456", "Lucía", "Mendoza", "lucia@correo.pe", "987654321",
                "Futbol2026", "Futbol2026", true, true);
    }

    @Test
    void siOtroRegistroGanoConElMismoDniResponde409Dni() {
        when(cuentas.existsByDni("70123456")).thenReturn(false, true);
        when(cuentas.existsByCorreo("lucia@correo.pe")).thenReturn(false, false);
        when(cuentas.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_cuenta_cliente_dni"));
        assertThatThrownBy(() -> servicio.registrar(solicitud()))
                .isInstanceOf(NegocioExcepcion.class).extracting("codigo").isEqualTo("DNI_REGISTRADO");
    }

    @Test
    void siOtroRegistroGanoConElMismoCorreoResponde409Correo() {
        when(cuentas.existsByDni("70123456")).thenReturn(false, false);
        when(cuentas.existsByCorreo("lucia@correo.pe")).thenReturn(false, true);
        when(cuentas.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_cuenta_cliente_correo"));
        assertThatThrownBy(() -> servicio.registrar(solicitud()))
                .isInstanceOf(NegocioExcepcion.class).extracting("codigo").isEqualTo("CORREO_REGISTRADO");
    }

    @Test
    void unaContrasenaDeMasDe72BytesNoCoincide() {
        var cuenta = new pe.alcanfores.auth.modelo.CuentaCliente();
        cuenta.setContrasenaHash("no-es-un-hash-bcrypt");
        cuenta.setCorreo("lucia@correo.pe");
        when(cuentas.buscarParaLogin("lucia@correo.pe")).thenReturn(Optional.of(cuenta));
        assertThatThrownBy(() -> servicio.iniciarSesion(new LoginClienteSolicitud("lucia@correo.pe", "x".repeat(80))))
                .isInstanceOf(NegocioExcepcion.class).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
    }
}

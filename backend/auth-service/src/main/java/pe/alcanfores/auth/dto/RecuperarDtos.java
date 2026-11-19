package pe.alcanfores.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.alcanfores.auth.modelo.CodigoRecuperacion.Canal;
import pe.alcanfores.auth.modelo.CodigoRecuperacion.TipoCuenta;
import pe.alcanfores.auth.validacion.ContrasenaSegura;
import pe.alcanfores.auth.validacion.ContrasenasCoinciden;

import java.time.LocalDateTime;
import java.util.List;

/** Contratos de «¿Olvidaste tu contraseña?» (públicos, con límite de 5 por minuto por IP en el gateway). */
public final class RecuperarDtos {

    static final String MENSAJE_TIPO = "Indica el tipo de cuenta: CLIENTE o ADMIN.";
    static final String MENSAJE_USUARIO = "Ingresa tu DNI, usuario o correo.";

    private RecuperarDtos() {
    }

    /** {@code usuario}: cliente = DNI o correo; panel = usuario o correo. */
    public record OpcionesSolicitud(
            @NotNull(message = MENSAJE_TIPO) TipoCuenta tipoCuenta,
            @NotBlank(message = MENSAJE_USUARIO) @Size(max = 100, message = MENSAJE_USUARIO) String usuario) {
    }

    /** {@code destino} enmascarado: «l****@gmail.com» o «*** *** 254». */
    public record CanalDisponible(Canal canal, String destino) {
    }

    public record OpcionesRespuesta(List<CanalDisponible> canales) {
    }

    public record EnviarSolicitud(
            @NotNull(message = MENSAJE_TIPO) TipoCuenta tipoCuenta,
            @NotBlank(message = MENSAJE_USUARIO) @Size(max = 100, message = MENSAJE_USUARIO) String usuario,
            @NotNull(message = "Elige por dónde recibir el código: CORREO o WHATSAPP.") Canal canal) {
    }

    /**
     * {@code simulado}: el canal aún no está configurado (el código no llegó a ningún lado). {@code codigoPrueba} solo
     * existe si el servidor corre con {@code RECUPERAR_DEVOLVER_CODIGO=true} (pruebas locales, NUNCA en producción).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EnvioRespuesta(Canal canal, String destino, LocalDateTime expiraEn, LocalDateTime reenviarEn,
                                 int reenviarEnSegundos, boolean simulado, String codigoPrueba) {
    }

    public record VerificarSolicitud(
            @NotNull(message = MENSAJE_TIPO) TipoCuenta tipoCuenta,
            @NotBlank(message = MENSAJE_USUARIO) @Size(max = 100, message = MENSAJE_USUARIO) String usuario,
            @NotBlank(message = "Ingresa el código de 6 dígitos.")
            @Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos.") String codigo) {

        @Override
        public String toString() {
            return "VerificarSolicitud[" + tipoCuenta + ", " + usuario + ", codigo=******]";
        }
    }

    public record VerificacionRespuesta(String tokenRestablecer, LocalDateTime expiraEn) {
    }

    @ContrasenasCoinciden
    public record RestablecerSolicitud(
            @NotBlank(message = "Vuelve a pedir un código para cambiar tu contraseña.")
            @Size(max = 100, message = "Vuelve a pedir un código para cambiar tu contraseña.") String tokenRestablecer,
            @NotBlank(message = "Ingresa la contraseña nueva.") @ContrasenaSegura String nueva,
            @NotBlank(message = "Repite la contraseña nueva.") String confirmacion)
            implements ContrasenasCoinciden.ConConfirmacion {

        @Override
        public String contrasenaAConfirmar() {
            return nueva;
        }

        @Override
        public String toString() {
            return "RestablecerSolicitud[***]";
        }
    }
}

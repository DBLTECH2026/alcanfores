package pe.alcanfores.notificaciones.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.SeguridadCorreo;

import java.time.LocalDateTime;

/** Contratos del correo configurable desde el panel y del envío interno de códigos (auth-service). */
public final class CorreoDtos {

    /** Correo con dominio que tenga al menos un punto (ej. reservas@alcanfores.pe). */
    public static final String CORREO = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$";
    /** Nombre de servidor o IP, sin protocolo ni espacios (ej. smtp.hostinger.com). */
    public static final String HOST = "^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$";
    static final String MENSAJE_HOST = "Escribe solo el nombre del servidor, sin http:// ni espacios (por ejemplo, smtp.hostinger.com).";
    static final String MENSAJE_PUERTO = "El puerto va de 1 a 65535 (Hostinger: 465 con SSL o 587 con STARTTLS).";
    static final String MENSAJE_USUARIO = "El usuario es el correo completo del buzón (por ejemplo, reservas@tudominio.com).";
    static final String MENSAJE_REMITENTE = "Ingresa el correo que verá el cliente como remitente (hasta 100 caracteres).";

    private CorreoDtos() {
    }

    /**
     * Estado del correo para el panel. {@code origen}: PANEL (guardado aquí), VARIABLES ({@code MAIL_*} del servidor) o
     * NINGUNO (los avisos quedan simulados). Nunca incluye la contraseña: solo {@code tieneClave}.
     */
    public record ConfigCorreoRespuesta(boolean configurado, String origen, String host, Integer puerto, String usuario,
                                        String remitente, String nombreRemitente, String seguridad, boolean tieneClave,
                                        LocalDateTime actualizadoEn, String actualizadoPor) {
    }

    /** {@code clave} vacía o ausente = conservar la guardada. */
    public record GuardarCorreoSolicitud(
            @NotBlank(message = MENSAJE_HOST) @Size(max = 120, message = MENSAJE_HOST)
            @Pattern(regexp = HOST, message = MENSAJE_HOST) String host,
            @NotNull(message = MENSAJE_PUERTO) @Min(value = 1, message = MENSAJE_PUERTO)
            @Max(value = 65535, message = MENSAJE_PUERTO) Integer puerto,
            @NotBlank(message = MENSAJE_USUARIO) @Size(max = 100, message = MENSAJE_USUARIO)
            @Email(regexp = CORREO, message = MENSAJE_USUARIO) String usuario,
            @Size(max = 200, message = "La contraseña tiene como máximo 200 caracteres.") String clave,
            @NotBlank(message = MENSAJE_REMITENTE) @Size(max = 100, message = MENSAJE_REMITENTE)
            @Email(regexp = CORREO, message = MENSAJE_REMITENTE) String remitente,
            @Size(max = 80, message = "El nombre del remitente tiene como máximo 80 caracteres.")
            @Pattern(regexp = "^[^<>\"\\r\\n]*$", message = "El nombre del remitente no puede llevar < > ni comillas.")
            String nombreRemitente,
            @NotNull(message = "Elige la seguridad: SSL (puerto 465) o STARTTLS (puerto 587).") SeguridadCorreo seguridad) {

        @Override
        public String toString() {
            return "GuardarCorreoSolicitud[" + host + ":" + puerto + " " + seguridad + ", usuario=" + usuario + ", clave=***]";
        }
    }

    public record PruebaCorreoSolicitud(
            @NotBlank(message = "Ingresa el correo al que enviaremos la prueba.")
            @Size(max = 100, message = "Ingresa un correo válido de hasta 100 caracteres.")
            @Email(regexp = CORREO, message = "Ingresa un correo válido de hasta 100 caracteres.") String destino) {
    }

    public record PruebaCorreoRespuesta(boolean enviado, String mensaje) {
    }

    public enum MotivoCodigo { RECUPERAR_CONTRASENA, CONTRASENA_CAMBIADA }

    /**
     * {@code POST /interno/codigos} (solo entre servicios): código de 6 dígitos para cambiar la contraseña o aviso de
     * que la contraseña se cambió. {@code destino}: correo o celular peruano de 9 dígitos, según {@code canal}.
     */
    public record CodigoInternoSolicitud(
            @NotNull(message = "Indica el canal: CORREO o WHATSAPP.") CanalNotificacion canal,
            @NotBlank(message = "Indica el destino.") @Size(max = 120, message = "El destino es demasiado largo.") String destino,
            @Size(max = 120, message = "El nombre es demasiado largo.") String nombre,
            @Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos.") String codigo,
            @NotNull(message = "Indica el motivo.") MotivoCodigo motivo) {

        @Override
        public String toString() {
            return "CodigoInternoSolicitud[" + canal + ", " + motivo + ", codigo=******]";
        }
    }

    /** {@code estado}: ENVIADO, SIMULADO (canal sin configurar) o FALLIDO ({@code detalle} dice por qué). */
    public record EnvioCodigoRespuesta(String estado, boolean simulado, String detalle) {
    }
}

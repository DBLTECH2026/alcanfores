package pe.alcanfores.auth.dto;

import java.time.LocalDateTime;

/** {@code {token, tipo:"Bearer", expiraEn, cliente:{id, dni, nombres, apellidos, correo, telefono}}} */
public record LoginClienteRespuesta(String token, String tipo, LocalDateTime expiraEn, ClienteSesion cliente) {

    public static LoginClienteRespuesta bearer(String token, LocalDateTime expiraEn, ClienteSesion cliente) {
        return new LoginClienteRespuesta(token, "Bearer", expiraEn, cliente);
    }
}

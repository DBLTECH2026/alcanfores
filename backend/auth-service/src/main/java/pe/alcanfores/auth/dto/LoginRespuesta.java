package pe.alcanfores.auth.dto;

import java.time.LocalDateTime;

/** {@code {token, tipo:"Bearer", expiraEn, usuario:{id, nombres, apellidos, usuario, correo, rol}}} */
public record LoginRespuesta(String token, String tipo, LocalDateTime expiraEn, UsuarioSesion usuario) {

    public static LoginRespuesta bearer(String token, LocalDateTime expiraEn, UsuarioSesion usuario) {
        return new LoginRespuesta(token, "Bearer", expiraEn, usuario);
    }
}

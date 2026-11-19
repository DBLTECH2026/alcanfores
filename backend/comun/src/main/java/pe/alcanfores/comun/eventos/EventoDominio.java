package pe.alcanfores.comun.eventos;

import java.time.LocalDateTime;
import java.util.UUID;

/** Todo evento de dominio tiene un identificador único (para consumir de forma idempotente) y su fecha. */
public interface EventoDominio {
    UUID idEvento();

    LocalDateTime ocurridoEn();
}

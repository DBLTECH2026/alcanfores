package pe.alcanfores.reservas.evento;

import pe.alcanfores.comun.eventos.EventoDominio;

/** Evento de dominio que se enviará a RabbitMQ cuando la transacción actual termine bien (AFTER_COMMIT). */
public record EventoParaPublicar(String routingKey, EventoDominio evento) {
}

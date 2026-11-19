package pe.alcanfores.notificaciones.dto;

import pe.alcanfores.comun.validacion.Celular;

/** Opcional: con el celular del WhatsApp a vincular, Evolution devuelve además el código de emparejamiento. */
public record ConectarWhatsappSolicitud(@Celular String telefono) {
}

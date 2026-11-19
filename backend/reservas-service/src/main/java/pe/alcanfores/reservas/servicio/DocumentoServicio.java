package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.dto.DocumentoDtos.DniRespuesta;
import pe.alcanfores.reservas.dto.DocumentoDtos.RucRespuesta;

/** Consulta de DNI (RENIEC) y RUC (SUNAT) con caché de 30 días. */
public interface DocumentoServicio {

    DniRespuesta consultarDni(String dni);

    RucRespuesta consultarRuc(String ruc);
}

package pe.alcanfores.reservas.servicio.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.reservas.cliente.ProveedorDocumentos;
import pe.alcanfores.reservas.dto.DocumentoDtos.DniRespuesta;
import pe.alcanfores.reservas.dto.DocumentoDtos.RucRespuesta;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.modelo.ConsultaDocumento;
import pe.alcanfores.reservas.modelo.TipoDocumento;
import pe.alcanfores.reservas.repositorio.ConsultaDocumentoRepositorio;
import pe.alcanfores.reservas.servicio.DocumentoServicio;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class DocumentoServicioImpl implements DocumentoServicio {

    public static final String FUENTE_CACHE = "CACHE";
    private static final int DIAS_CACHE = 30;
    /** El «no encontrado» se guarda 1 día (puede ser un DNI recién emitido o un error de tipeo corregido luego). */
    private static final int DIAS_CACHE_NO_ENCONTRADO = 1;
    private static final Logger log = LoggerFactory.getLogger(DocumentoServicioImpl.class);

    private final ProveedorDocumentos proveedor;
    private final ConsultaDocumentoRepositorio cache;
    private final ObjectMapper json;

    @Override
    // El 404 no revierte la transacción: así se guarda la caché del «no encontrado».
    @Transactional(noRollbackFor = NegocioExcepcion.class)
    public DniRespuesta consultarDni(String dni) {
        String numero = dni == null ? "" : dni.trim();
        if (!Documentos.dniValido(numero)) {
            throw NegocioExcepcion.validacion("dni", Mensajes.DNI);
        }
        return consultar(TipoDocumento.DNI, numero, DniRespuesta.class,
                () -> proveedor.consultarDni(numero),
                () -> new NegocioExcepcion(404, "DNI_NO_ENCONTRADO",
                        "No encontramos ese DNI en RENIEC. Revisa el número; si está bien, escribe tus nombres y apellidos a mano."),
                DniRespuesta::fuente,
                r -> r.conFuente(FUENTE_CACHE));
    }

    @Override
    // El 404 no revierte la transacción: así se guarda la caché del «no encontrado».
    @Transactional(noRollbackFor = NegocioExcepcion.class)
    public RucRespuesta consultarRuc(String ruc) {
        String numero = ruc == null ? "" : ruc.trim();
        if (!Documentos.rucValido(numero)) {
            throw NegocioExcepcion.validacion("ruc", Mensajes.RUC);
        }
        return consultar(TipoDocumento.RUC, numero, RucRespuesta.class,
                () -> proveedor.consultarRuc(numero),
                () -> new NegocioExcepcion(404, "RUC_NO_ENCONTRADO",
                        "No encontramos ese RUC en SUNAT. Revisa el número; si está bien, escribe la razón social y la dirección a mano."),
                RucRespuesta::fuente,
                r -> r.conFuente(FUENTE_CACHE));
    }

    private <T> T consultar(TipoDocumento tipo, String numero, Class<T> clase, Supplier<Optional<T>> remoto,
                            Supplier<NegocioExcepcion> noExiste, Function<T, String> fuente,
                            UnaryOperator<T> comoCache) {
        ConsultaDocumento.Clave clave = new ConsultaDocumento.Clave(tipo, numero);
        LocalDateTime ahora = Tiempo.ahora();
        Optional<ConsultaDocumento> guardada = cache.findById(clave);
        if (guardada.isPresent()) {
            ConsultaDocumento c = guardada.get();
            if (!c.encontrado() && c.getConsultadoEn().isAfter(ahora.minusDays(DIAS_CACHE_NO_ENCONTRADO))) {
                // El proveedor ya dijo hace poco que no existe: no se gasta otra consulta (Factiliza cobra cada una).
                throw noExiste.get();
            }
            if (c.encontrado() && c.getConsultadoEn().isAfter(ahora.minusDays(DIAS_CACHE))) {
                try {
                    return comoCache.apply(json.readValue(c.getRespuesta(), clase));
                } catch (JsonProcessingException e) {
                    log.warn("Caché ilegible para {} {}; se vuelve a consultar", tipo, numero);
                }
            }
        }
        if (!proveedor.disponible()) {
            throw NegocioExcepcion.noDisponible("CONSULTA_NO_DISPONIBLE", "La consulta a "
                    + (tipo == TipoDocumento.DNI ? "RENIEC" : "SUNAT") + " no está disponible en este momento. "
                    + "Escribe los datos a mano.");
        }
        Optional<T> respuesta = remoto.get();
        ConsultaDocumento c = guardada.orElseGet(ConsultaDocumento::new);
        c.setClave(clave);
        c.setConsultadoEn(Tiempo.ahora());
        if (respuesta.isEmpty()) {
            c.setFuente(ConsultaDocumento.FUENTE_NO_ENCONTRADO);
            c.setRespuesta("{}");
            cache.save(c);
            throw noExiste.get();
        }
        c.setFuente(fuente.apply(respuesta.get()));
        try {
            c.setRespuesta(json.writeValueAsString(respuesta.get()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la consulta de documento", e);
        }
        cache.save(c);
        return respuesta.get();
    }
}

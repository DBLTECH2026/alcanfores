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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class DocumentoServicioImpl implements DocumentoServicio {

    public static final String FUENTE_CACHE = "CACHE";
    private static final int DIAS_CACHE = 30;
    private static final Logger log = LoggerFactory.getLogger(DocumentoServicioImpl.class);

    private final ProveedorDocumentos proveedor;
    private final ConsultaDocumentoRepositorio cache;
    private final ObjectMapper json;

    @Override
    @Transactional
    public DniRespuesta consultarDni(String dni) {
        String numero = dni == null ? "" : dni.trim();
        if (!Documentos.dniValido(numero)) {
            throw NegocioExcepcion.validacion("dni", Mensajes.DNI);
        }
        return consultar(TipoDocumento.DNI, numero, DniRespuesta.class,
                () -> proveedor.consultarDni(numero),
                () -> new NegocioExcepcion(404, "DNI_NO_ENCONTRADO",
                        "No encontramos ese DNI en RENIEC. Revisa el número o escribe tus datos a mano."),
                DniRespuesta::fuente,
                r -> r.conFuente(FUENTE_CACHE));
    }

    @Override
    @Transactional
    public RucRespuesta consultarRuc(String ruc) {
        String numero = ruc == null ? "" : ruc.trim();
        if (!Documentos.rucValido(numero)) {
            throw NegocioExcepcion.validacion("ruc", Mensajes.RUC);
        }
        return consultar(TipoDocumento.RUC, numero, RucRespuesta.class,
                () -> proveedor.consultarRuc(numero),
                () -> new NegocioExcepcion(404, "RUC_NO_ENCONTRADO",
                        "No encontramos ese RUC en SUNAT. Revisa el número o escribe la razón social a mano."),
                RucRespuesta::fuente,
                r -> r.conFuente(FUENTE_CACHE));
    }

    private <T> T consultar(TipoDocumento tipo, String numero, Class<T> clase, Supplier<Optional<T>> remoto,
                            Supplier<NegocioExcepcion> noExiste, Function<T, String> fuente,
                            UnaryOperator<T> comoCache) {
        ConsultaDocumento.Clave clave = new ConsultaDocumento.Clave(tipo, numero);
        Optional<ConsultaDocumento> guardada = cache.findById(clave)
                .filter(c -> c.getConsultadoEn().isAfter(Tiempo.ahora().minusDays(DIAS_CACHE)));
        if (guardada.isPresent()) {
            try {
                return comoCache.apply(json.readValue(guardada.get().getRespuesta(), clase));
            } catch (JsonProcessingException e) {
                log.warn("Caché ilegible para {} {}; se vuelve a consultar", tipo, numero);
            }
        }
        if (!proveedor.disponible()) {
            throw NegocioExcepcion.noDisponible("CONSULTA_NO_DISPONIBLE", "La consulta a "
                    + (tipo == TipoDocumento.DNI ? "RENIEC" : "SUNAT") + " no está disponible en este momento. "
                    + "Escribe los datos a mano.");
        }
        T respuesta = remoto.get().orElseThrow(noExiste);
        ConsultaDocumento c = cache.findById(clave).orElseGet(ConsultaDocumento::new);
        c.setClave(clave);
        c.setFuente(fuente.apply(respuesta));
        c.setConsultadoEn(Tiempo.ahora());
        try {
            c.setRespuesta(json.writeValueAsString(respuesta));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la consulta de documento", e);
        }
        cache.save(c);
        return respuesta;
    }
}

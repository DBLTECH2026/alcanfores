package pe.alcanfores.catalogo.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.catalogo.dto.ComplejoRespuesta;
import pe.alcanfores.catalogo.modelo.ClaveConfiguracion;
import pe.alcanfores.catalogo.modelo.Configuracion;
import pe.alcanfores.catalogo.repositorio.ConfiguracionRepositorio;
import pe.alcanfores.catalogo.servicio.ConfiguracionServicio;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pe.alcanfores.catalogo.modelo.ClaveConfiguracion.*;

@Service
@RequiredArgsConstructor
public class ConfiguracionServicioImpl implements ConfiguracionServicio {

    /** Claves que intervienen en las reglas entre valores (horario y días hacia adelante). */
    private static final Set<ClaveConfiguracion> DEL_HORARIO = Set.of(PRIMER_TURNO, ULTIMO_TURNO, INICIO_NOCHE, DURACION_MIN,
            DIAS_VISIBLES, DIAS_GENERADOS);

    private final ConfiguracionRepositorio repositorio;
    private final ValidadorConfiguracion validador;
    private final Auditor auditor;

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> todas() {
        Map<String, String> guardadas = repositorio.findAll().stream()
                .collect(Collectors.toMap(Configuracion::getClave, Configuracion::getValor));
        Map<String, String> resultado = new LinkedHashMap<>();
        for (ClaveConfiguracion c : ClaveConfiguracion.values()) {
            resultado.put(c.clave(), guardadas.getOrDefault(c.clave(), c.porDefecto()));
        }
        return resultado;
    }

    @Override
    @Transactional
    public Map<String, String> actualizar(Map<String, Object> cambios) {
        if (cambios == null || cambios.isEmpty()) {
            throw NegocioExcepcion.validacion("configuracion", "Envía al menos una clave para actualizar.");
        }
        Map<String, String> actual = todas();
        Map<String, String> combinada = new LinkedHashMap<>(actual);
        Map<ClaveConfiguracion, String> normalizados = new LinkedHashMap<>();
        List<ErrorCampo> errores = new ArrayList<>();

        cambios.forEach((clave, valor) -> {
            var c = ClaveConfiguracion.de(clave);
            if (c.isEmpty()) {
                errores.add(new ErrorCampo(clave, "Esta clave de configuración no existe."));
                return;
            }
            ValidadorConfiguracion.Resultado r = validador.validar(c.get(), texto(valor));
            if (r.valido()) {
                normalizados.put(c.get(), r.valor());
                combinada.put(c.get().clave(), r.valor());
            } else {
                errores.add(new ErrorCampo(clave, r.error()));
            }
        });
        boolean horarioSinErrores = errores.stream().noneMatch(e -> ClaveConfiguracion.de(e.campo())
                .map(DEL_HORARIO::contains).orElse(false));
        if (horarioSinErrores) {
            errores.addAll(validador.validarConjunto(combinada));
        }
        if (!errores.isEmpty()) {
            String mensaje = errores.size() == 1 ? errores.get(0).mensaje() : "Revisa los datos marcados.";
            throw new NegocioExcepcion(400, "VALIDACION", mensaje, errores);
        }

        List<String> detalle = new ArrayList<>();
        normalizados.forEach((c, valor) -> {
            if (!valor.equals(actual.get(c.clave()))) {
                repositorio.save(new Configuracion(c.clave(), valor, c.descripcion()));
                detalle.add(c.clave() + ": " + actual.get(c.clave()) + " → " + valor);
            }
        });
        if (!detalle.isEmpty()) {
            auditor.registrar("EDITAR_CONFIGURACION", "configuracion", null, String.join("; ", detalle));
        }
        return combinada;
    }

    @Override
    @Transactional(readOnly = true)
    public ComplejoRespuesta complejo() {
        Map<String, String> c = todas();
        Function<ClaveConfiguracion, String> v = k -> c.get(k.clave());
        Function<ClaveConfiguracion, Integer> n = k -> Integer.valueOf(c.get(k.clave()));
        List<Integer> recordatorios = ValidadorConfiguracion.minutos(v.apply(RECORDATORIO_MINUTOS));
        return new ComplejoRespuesta(v.apply(NOMBRE_COMPLEJO), v.apply(LEMA), v.apply(DIRECCION), v.apply(REFERENCIA),
                v.apply(WHATSAPP), v.apply(CORREO), v.apply(MAPA_URL), new BigDecimal(v.apply(LATITUD)),
                new BigDecimal(v.apply(LONGITUD)),
                new ComplejoRespuesta.Horario(v.apply(PRIMER_TURNO), v.apply(ULTIMO_TURNO), n.apply(DURACION_MIN),
                        v.apply(INICIO_NOCHE), n.apply(DIAS_VISIBLES)),
                new ComplejoRespuesta.Reglas(n.apply(ADELANTO_PCT), n.apply(DEVOLUCION_HORAS), n.apply(MINUTOS_PARA_PAGAR),
                        recordatorios.isEmpty() ? 0 : recordatorios.get(0) / 60, recordatorios, n.apply(ASISTENCIA_DESDE_MIN)),
                new ComplejoRespuesta.Pagos(v.apply(YAPE_NUMERO), v.apply(YAPE_TITULAR), v.apply(PLIN_NUMERO)),
                new ComplejoRespuesta.Asistente(Boolean.parseBoolean(v.apply(IA_WEB_ACTIVA))));
    }

    /** Acepta números o textos en el JSON ({@code "adelanto_pct": 40} o {@code "40"}). */
    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof BigDecimal b) {
            return b.toPlainString();
        }
        if (valor instanceof Number || valor instanceof String || valor instanceof Boolean) {
            return valor.toString();
        }
        return "";
    }
}

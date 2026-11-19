package pe.alcanfores.catalogo.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.catalogo.cliente.ReservasCliente;
import pe.alcanfores.catalogo.dto.CalendarioDtos.CargaFeriadosRespuesta;
import pe.alcanfores.catalogo.dto.CalendarioDtos.DiaEspecialRespuesta;
import pe.alcanfores.catalogo.dto.CalendarioDtos.DiaEspecialSolicitud;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.DiaEspecial;
import pe.alcanfores.catalogo.modelo.FeriadoModo;
import pe.alcanfores.catalogo.modelo.TipoDiaEspecial;
import pe.alcanfores.catalogo.repositorio.CanchaRepositorio;
import pe.alcanfores.catalogo.repositorio.DiaEspecialRepositorio;
import pe.alcanfores.catalogo.servicio.CalendarioServicio;
import pe.alcanfores.catalogo.util.FeriadosPeru;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarioServicioImpl implements CalendarioServicio {

    static final String NO_ENCONTRADO = "No encontramos ese día en el calendario.";
    static final int ANIO_MIN = 2000;
    static final int ANIO_MAX = 2100;

    private final DiaEspecialRepositorio dias;
    private final CanchaRepositorio canchas;
    private final ReservasCliente reservas;
    private final ApplicationEventPublisher eventos;
    private final Auditor auditor;

    @Override
    @Transactional(readOnly = true)
    public List<DiaEspecialRespuesta> delAnio(Integer anio) {
        int a = anio(anio);
        return dias.findByFechaBetweenOrderByFechaAsc(LocalDate.of(a, 1, 1), LocalDate.of(a, 12, 31)).stream()
                .map(CalendarioServicioImpl::respuesta).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiaEspecialRespuesta> entre(LocalDate desde, LocalDate hasta) {
        LocalDate d = desde == null ? Tiempo.hoy() : desde;
        LocalDate h = hasta == null ? d.plusDays(60) : hasta;
        if (h.isBefore(d)) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final debe ser igual o posterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(d, h) > 366) {
            throw NegocioExcepcion.validacion("hasta", "Consulta como máximo 366 días.");
        }
        return dias.findByFechaBetweenOrderByFechaAsc(d, h).stream().map(CalendarioServicioImpl::respuesta).toList();
    }

    @Override
    @Transactional
    public DiaEspecialRespuesta crear(DiaEspecialSolicitud s) {
        List<Long> afectadas = canchasDe(s);
        dias.findByFecha(s.fecha()).ifPresent(otro -> {
            throw duplicado(otro);
        });
        verificarReservas(s.fecha(), s.tipo(), afectadas);
        DiaEspecial d = dias.save(new DiaEspecial(s.fecha(), s.tipo(), Texto.limpiar(s.nombre()), afectadas));
        anunciar(d, false);
        auditor.registrar("CREAR_DIA_ESPECIAL", "dia_especial", d.getId(), resumen(d));
        return respuesta(d);
    }

    @Override
    @Transactional
    public DiaEspecialRespuesta actualizar(Long id, DiaEspecialSolicitud s) {
        DiaEspecial d = buscar(id);
        List<Long> afectadas = canchasDe(s);
        dias.findByFecha(s.fecha()).filter(otro -> !otro.getId().equals(id)).ifPresent(otro -> {
            throw duplicado(otro);
        });
        verificarReservas(s.fecha(), s.tipo(), afectadas);
        String antes = resumen(d);
        if (!d.getFecha().equals(s.fecha())) {
            anunciar(d, true); // la fecha vieja deja de ser especial
        }
        d.setFecha(s.fecha());
        d.setTipo(s.tipo());
        d.setNombre(Texto.limpiar(s.nombre()));
        d.setCanchas(afectadas);
        dias.saveAndFlush(d);
        anunciar(d, false);
        auditor.registrar("EDITAR_DIA_ESPECIAL", "dia_especial", id, antes + " → " + resumen(d));
        return respuesta(d);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        DiaEspecial d = buscar(id);
        dias.delete(d);
        anunciar(d, true);
        auditor.registrar("BORRAR_DIA_ESPECIAL", "dia_especial", id, resumen(d));
    }

    @Override
    @Transactional
    public CargaFeriadosRespuesta cargarFeriadosPeru(Integer anio) {
        int a = anio(anio);
        int creados = 0;
        int existentes = 0;
        for (FeriadosPeru.Feriado f : FeriadosPeru.del(a)) {
            if (dias.findByFecha(f.fecha()).isPresent()) {
                existentes++;
            } else {
                anunciar(dias.save(new DiaEspecial(f.fecha(), TipoDiaEspecial.FERIADO, f.nombre(), null)), false);
                creados++;
            }
        }
        auditor.registrar("CARGAR_FERIADOS_PERU", "dia_especial", null,
                "Feriados de Perú " + a + ": " + creados + " creados, " + existentes + " ya estaban");
        return new CargaFeriadosRespuesta(creados, existentes);
    }

    /** Canchas que cierra el día: solo en NO_LABORABLE (vacío o nulo = todas → null); deben existir. */
    private List<Long> canchasDe(DiaEspecialSolicitud s) {
        List<Long> ids = s.canchas() == null ? List.of() : s.canchas().stream().distinct().sorted().toList();
        if (ids.isEmpty()) {
            return null;
        }
        if (s.tipo() == TipoDiaEspecial.FERIADO) {
            throw NegocioExcepcion.validacion("canchas",
                    "Los feriados aplican a todas las canchas; elige canchas solo en un día no laborable.");
        }
        if (canchas.findAllById(ids).size() != ids.size()) {
            throw NegocioExcepcion.validacion("canchas", "Alguna de las canchas elegidas no existe.");
        }
        return ids;
    }

    /**
     * Un día que cierra canchas no puede caer sobre reservas vigentes: en NO_LABORABLE cuentan las canchas indicadas (o
     * todas); en FERIADO, las que cierran en feriado. Las fechas pasadas no se revisan.
     */
    private void verificarReservas(LocalDate fecha, TipoDiaEspecial tipo, List<Long> afectadas) {
        if (fecha.isBefore(Tiempo.hoy())) {
            return;
        }
        List<Long> ids = afectadas;
        if (tipo == TipoDiaEspecial.FERIADO) {
            ids = canchas.findByFeriadoModo(FeriadoModo.CERRADA).stream().map(Cancha::getId).toList();
            if (ids.isEmpty()) {
                return;
            }
        }
        List<String> codigos = reservas.reservasVigentes(fecha, ids);
        if (!codigos.isEmpty()) {
            String lista = String.join(", ", codigos);
            throw new NegocioExcepcion(409, "DIA_CON_RESERVAS", "Ese día ya hay reservas en esas canchas: " + lista
                    + ". Cancélalas o cámbialas antes.", List.of(new ErrorCampo("fecha", lista)));
        }
    }

    /** El evento sale a RabbitMQ después del commit (PublicadorCalendarioActualizado). */
    private void anunciar(DiaEspecial d, boolean eliminado) {
        eventos.publishEvent(new CalendarioActualizadoEvento(UUID.randomUUID(), Tiempo.ahora(), d.getFecha(), d.getTipo().name(),
                d.getNombre(), d.getCanchas(), eliminado));
    }

    private static NegocioExcepcion duplicado(DiaEspecial otro) {
        return new NegocioExcepcion(409, "DIA_ESPECIAL_DUPLICADO", "Ese día ya está en el calendario como «"
                + otro.getNombre() + "».", List.of(new ErrorCampo("fecha", "Ese día ya está en el calendario.")));
    }

    private static int anio(Integer anio) {
        int a = anio == null ? Tiempo.hoy().getYear() : anio;
        if (a < ANIO_MIN || a > ANIO_MAX) {
            throw NegocioExcepcion.validacion("anio", "Elige un año entre " + ANIO_MIN + " y " + ANIO_MAX + ".");
        }
        return a;
    }

    private DiaEspecial buscar(Long id) {
        return dias.findById(id).orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADO));
    }

    private static String resumen(DiaEspecial d) {
        return d.getFecha().format(Tiempo.FECHA_CORTA) + " · " + d.getTipo() + " · " + d.getNombre()
                + (d.getCanchas() == null ? " · todas las canchas" : " · canchas " + d.getCanchas());
    }

    static DiaEspecialRespuesta respuesta(DiaEspecial d) {
        return new DiaEspecialRespuesta(d.getId(), d.getFecha(), d.getTipo().name(), d.getNombre(), d.getCanchas());
    }
}

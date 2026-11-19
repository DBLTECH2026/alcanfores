package pe.alcanfores.catalogo.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.catalogo.dto.HorarioDtos.DiaSolicitud;
import pe.alcanfores.catalogo.dto.HorarioDtos.HorarioCanchaRespuesta;
import pe.alcanfores.catalogo.dto.HorarioDtos.HorarioSolicitud;
import pe.alcanfores.catalogo.dto.HorarioDtos.TurnoVistaPrevia;
import pe.alcanfores.catalogo.mapeador.CanchaMapeador;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.HorarioCancha;
import pe.alcanfores.catalogo.repositorio.CanchaRepositorio;
import pe.alcanfores.catalogo.servicio.HorarioServicio;
import pe.alcanfores.catalogo.util.Horas;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Horarios;
import pe.alcanfores.comun.util.Tiempo;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HorarioServicioImpl implements HorarioServicio {

    static final LocalTime APERTURA_POR_DEFECTO = LocalTime.of(8, 0);
    static final LocalTime CIERRE_POR_DEFECTO = LocalTime.of(23, 0);

    private final CanchaRepositorio canchas;
    private final CanchaMapeador mapeador;
    private final ApplicationEventPublisher eventos;
    private final Auditor auditor;

    @Override
    @Transactional(readOnly = true)
    public HorarioCanchaRespuesta horario(Long idCancha) {
        return mapeador.horarioCompleto(buscar(idCancha));
    }

    @Override
    @Transactional
    public HorarioCanchaRespuesta guardar(Long idCancha, HorarioSolicitud s) {
        Cancha c = buscar(idCancha);
        List<HorarioCancha> semana = validar(s);
        c.setDuracionTurnoMin(s.duracionTurnoMin());
        c.setInicioNoche(Horas.leer(s.inicioNoche()));
        c.setFeriadoModo(s.feriado().modo());
        c.setRecargoFeriado(Dinero.redondear(s.feriado().recargo()));
        c.getHorario().clear();
        c.getHorario().addAll(semana);
        c.setActualizadoEn(Tiempo.ahora());
        canchas.saveAndFlush(c);
        // El evento sale a RabbitMQ después del commit (PublicadorCanchaActualizada).
        eventos.publishEvent(mapeador.evento(c));
        auditor.registrar("EDITAR_HORARIO_CANCHA", "cancha", c.getId(), c.getNombre() + " · " + Horas.resumen(semana)
                + " · turnos de " + c.getDuracionTurnoMin() + " min · noche desde " + Horas.formato(c.getInicioNoche())
                + " · feriado " + c.getFeriadoModo() + " (+" + Dinero.formato(c.getRecargoFeriado()) + ")");
        return mapeador.horarioCompleto(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoVistaPrevia> vistaPrevia(Long idCancha, int dia) {
        validarDia(dia);
        Cancha c = buscar(idCancha);
        HorarioCancha h = c.getHorario().stream().filter(d -> d.getDiaSemana() == dia).findFirst().orElse(null);
        return turnos(h, c.getDuracionTurnoMin(), c.getInicioNoche(), c.getPrecioDia(), c.getPrecioNoche());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoVistaPrevia> vistaPrevia(Long idCancha, int dia, HorarioSolicitud s) {
        validarDia(dia);
        Cancha c = buscar(idCancha);
        List<HorarioCancha> semana = validar(s);
        return turnos(semana.get(dia - 1), s.duracionTurnoMin(), Horas.leer(s.inicioNoche()), c.getPrecioDia(),
                c.getPrecioNoche());
    }

    /** Horario por defecto de una cancha nueva: todos los días con el horario global del complejo, ajustado a la duración. */
    static List<HorarioCancha> semanaPorDefecto(LocalTime apertura, LocalTime cierre, int duracion) {
        int inicio = Horarios.minutos(apertura);
        int enteros = (Horarios.minutos(cierre) - inicio) / duracion;
        LocalTime fin = enteros > 0 ? Horarios.hora(inicio + enteros * duracion) : cierre;
        List<HorarioCancha> semana = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            semana.add(new HorarioCancha(d, true, apertura, fin));
        }
        return semana;
    }

    private static List<TurnoVistaPrevia> turnos(HorarioCancha h, int duracion, LocalTime inicioNoche, BigDecimal precioDia,
                                                 BigDecimal precioNoche) {
        if (h == null || !h.isAbierto()) {
            return List.of();
        }
        int noche = Horarios.minutos(inicioNoche);
        return Horas.inicios(h.getApertura(), h.getCierre(), duracion).stream()
                .map(m -> new TurnoVistaPrevia(Horas.formato(Horarios.hora(m)), Horas.formato(Horarios.hora(m + duracion)),
                        m >= noche ? precioNoche : precioDia, m >= noche ? "NOCHE" : "DIA"))
                .toList();
    }

    /** Reglas entre campos (lo simple ya lo revisó Bean Validation). Devuelve los 7 días ordenados de lunes a domingo. */
    List<HorarioCancha> validar(HorarioSolicitud s) {
        List<ErrorCampo> errores = new ArrayList<>();
        int duracion = s.duracionTurnoMin();
        boolean duracionValida = duracion % 15 == 0;
        if (!duracionValida) {
            errores.add(new ErrorCampo("duracionTurnoMin", "La duración del turno va de 30 a 240 minutos, en múltiplos de 15."));
        }
        Set<Integer> vistos = new HashSet<>();
        for (DiaSolicitud d : s.dias()) {
            vistos.add(d.diaSemana());
        }
        if (vistos.size() != 7) {
            errores.add(new ErrorCampo("dias", "Envía los 7 días de la semana, de lunes (1) a domingo (7), sin repetir."));
        }
        HorarioCancha[] semana = new HorarioCancha[7];
        for (int i = 0; i < s.dias().size(); i++) {
            DiaSolicitud d = s.dias().get(i);
            LocalTime apertura = Horas.leer(d.apertura());
            LocalTime cierre = Horas.leer(d.cierre());
            String campo = "dias[" + i + "].";
            if (Boolean.TRUE.equals(d.abierto())) {
                if (apertura == null) {
                    errores.add(new ErrorCampo(campo + "apertura", "Indica la hora de apertura."));
                }
                if (cierre == null) {
                    errores.add(new ErrorCampo(campo + "cierre", "Indica la hora de cierre."));
                }
                if (apertura != null && cierre != null) {
                    String problema = problemaDelDia(apertura, cierre, duracion, duracionValida);
                    if (problema != null) {
                        errores.add(new ErrorCampo(campo + "cierre", problema));
                    }
                }
            }
            semana[d.diaSemana() - 1] = new HorarioCancha(d.diaSemana(), d.abierto(),
                    apertura == null ? APERTURA_POR_DEFECTO : apertura, cierre == null ? CIERRE_POR_DEFECTO : cierre);
        }
        if (!errores.isEmpty()) {
            boolean mismoMensaje = errores.stream().map(ErrorCampo::mensaje).distinct().count() == 1;
            throw new NegocioExcepcion(400, "VALIDACION", mismoMensaje ? errores.get(0).mensaje() : "Revisa los datos marcados.",
                    errores);
        }
        return List.of(semana);
    }

    /** Cierre antes de la apertura, que no entre ni un turno o que sobre tiempo (con las horas a las que conviene ajustar). */
    static String problemaDelDia(LocalTime apertura, LocalTime cierre, int duracion, boolean duracionValida) {
        int inicio = Horarios.minutos(apertura);
        int fin = Horarios.minutos(cierre);
        if (fin <= inicio) {
            return "El cierre debe ser posterior a la apertura.";
        }
        if (!duracionValida) {
            return null;
        }
        int total = fin - inicio;
        if (total < duracion) {
            return "Entre la apertura y el cierre no entra ni un turno de " + duracionTexto(duracion) + ".";
        }
        int sobra = total % duracion;
        if (sobra == 0) {
            return null;
        }
        List<String> opciones = new ArrayList<>();
        opciones.add(Tiempo.horaAmPm(Horarios.hora(fin - sobra)));
        if (fin + duracion - sobra <= 24 * 60) {
            opciones.add(Tiempo.horaAmPm(Horarios.hora(fin + duracion - sobra)));
        }
        return "Con turnos de " + duracionTexto(duracion) + ", de " + Tiempo.horaAmPm(apertura) + " a " + Tiempo.horaAmPm(cierre)
                + " sobra " + sobraTexto(sobra) + ": ajusta el cierre a " + String.join(" o a ", opciones);
    }

    /** «2 h», «45 min», «1 h 30 min». */
    static String duracionTexto(int minutos) {
        if (minutos % 60 == 0) {
            return minutos / 60 + " h";
        }
        return minutos < 60 ? minutos + " min" : minutos / 60 + " h " + minutos % 60 + " min";
    }

    /** «1 hora», «2 horas», «30 minutos», «1 hora y 30 minutos». */
    static String sobraTexto(int minutos) {
        int h = minutos / 60;
        int m = minutos % 60;
        String horas = h == 1 ? "1 hora" : h + " horas";
        if (m == 0) {
            return horas;
        }
        String mins = m + " minutos";
        return h == 0 ? mins : horas + " y " + mins;
    }

    private static void validarDia(int dia) {
        if (dia < 1 || dia > 7) {
            throw NegocioExcepcion.validacion("dia", "Elige un día de la semana del 1 (lunes) al 7 (domingo).");
        }
    }

    private Cancha buscar(Long id) {
        return canchas.findById(id).orElseThrow(() -> NegocioExcepcion.noEncontrado(CanchaServicioImpl.NO_ENCONTRADA));
    }
}

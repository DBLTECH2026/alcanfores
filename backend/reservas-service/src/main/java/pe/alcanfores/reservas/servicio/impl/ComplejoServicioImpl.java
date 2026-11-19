package pe.alcanfores.reservas.servicio.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.CatalogoCliente;
import pe.alcanfores.reservas.cliente.DiaEspecial;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.servicio.ComplejoServicio;

import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ComplejoServicioImpl implements ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicioImpl.class);

    private final CatalogoCliente catalogo;
    private volatile ParametrosComplejo parametros = ParametrosComplejo.porDefecto();
    private final Map<Long, CanchaCatalogo> canchas = new ConcurrentHashMap<>();
    private final Map<LocalDate, DiaEspecial> calendario = new ConcurrentHashMap<>();
    private volatile boolean canchasLeidas;
    private volatile boolean calendarioLeido;

    public ComplejoServicioImpl(CatalogoCliente catalogo) {
        this.catalogo = catalogo;
        CanchaCatalogo.semilla().forEach(c -> canchas.put(c.id(), c));
    }

    @Override
    public ParametrosComplejo parametros() {
        return parametros;
    }

    @Override
    public List<CanchaCatalogo> canchas() {
        return canchas.values().stream().sorted(Comparator.comparing(CanchaCatalogo::id)).toList();
    }

    @Override
    public Optional<CanchaCatalogo> cancha(Long idCancha) {
        return idCancha == null ? Optional.empty() : Optional.ofNullable(canchas.get(idCancha));
    }

    @Override
    public List<CanchaCatalogo> refrescar() {
        try {
            parametros = ParametrosComplejo.desde(catalogo.configuracion());
        } catch (RuntimeException e) {
            log.warn("Catálogo no respondió la configuración; se mantienen los valores actuales ({})", e.getMessage());
        }
        List<CanchaCatalogo> cambiadas = new ArrayList<>();
        try {
            List<CanchaCatalogo> recibidas = catalogo.canchas();
            if (recibidas != null) {
                canchasLeidas = true;
                for (CanchaCatalogo c : recibidas) {
                    if (c != null && c.id() != null && c.precioDia() != null && c.precioNoche() != null) {
                        CanchaCatalogo anterior = canchas.put(c.id(), c);
                        if (!c.mismosDatos(anterior)) {
                            cambiadas.add(c);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Catálogo no respondió las canchas; se mantienen las actuales ({})", e.getMessage());
        }
        return cambiadas;
    }

    @Override
    public void actualizarCancha(CanchaCatalogo cancha) {
        canchas.put(cancha.id(), cancha);
    }

    @Override
    public Optional<DiaEspecial> diaEspecial(LocalDate fecha) {
        return fecha == null ? Optional.empty() : Optional.ofNullable(calendario.get(fecha));
    }

    @Override
    public List<LocalDate> refrescarCalendario() {
        LocalDate hoy = Tiempo.hoy();
        List<DiaEspecial> recibidos;
        try {
            recibidos = catalogo.calendario(hoy, hoy.plusDays(parametros.horizonte()));
        } catch (RuntimeException e) {
            log.warn("Catálogo no respondió el calendario; se mantiene el actual ({})", e.getMessage());
            return List.of();
        }
        Map<LocalDate, DiaEspecial> nuevo = new java.util.HashMap<>();
        if (recibidos != null) {
            recibidos.stream().filter(d -> d != null && d.fecha() != null && d.tipo() != null)
                    .forEach(d -> nuevo.put(d.fecha(), d));
        }
        Set<LocalDate> fechas = new HashSet<>(calendario.keySet());
        fechas.addAll(nuevo.keySet());
        List<LocalDate> cambiadas = fechas.stream()
                .filter(f -> !Objects.equals(sinId(calendario.get(f)), sinId(nuevo.get(f)))).sorted().toList();
        calendario.keySet().retainAll(nuevo.keySet());
        calendario.putAll(nuevo);
        calendarioLeido = true;
        return cambiadas;
    }

    @Override
    public boolean catalogoLeido() {
        return canchasLeidas && calendarioLeido;
    }

    @Override
    public void actualizarDia(LocalDate fecha, DiaEspecial dia) {
        if (dia == null) {
            calendario.remove(fecha);
        } else {
            calendario.put(fecha, dia);
        }
    }

    /** El id no importa para saber si cambió (el evento no lo trae). */
    private static DiaEspecial sinId(DiaEspecial d) {
        return d == null ? null : new DiaEspecial(null, d.fecha(), d.tipo(), d.nombre(),
                d.canchas() == null || d.canchas().isEmpty() ? null : d.canchas());
    }
}

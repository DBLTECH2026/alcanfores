package pe.alcanfores.reservas.servicio.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.CatalogoCliente;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.servicio.ComplejoServicio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ComplejoServicioImpl implements ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicioImpl.class);

    private final CatalogoCliente catalogo;
    private volatile ParametrosComplejo parametros = ParametrosComplejo.porDefecto();
    private final Map<Long, CanchaCatalogo> canchas = new ConcurrentHashMap<>();

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
                for (CanchaCatalogo c : recibidas) {
                    if (c != null && c.id() != null && c.precioDia() != null && c.precioNoche() != null) {
                        CanchaCatalogo anterior = canchas.put(c.id(), c);
                        if (!iguales(anterior, c)) {
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

    private static boolean iguales(CanchaCatalogo a, CanchaCatalogo b) {
        return a != null && Objects.equals(a.nombre(), b.nombre()) && Objects.equals(a.deporte(), b.deporte())
                && a.precioDia().compareTo(b.precioDia()) == 0 && a.precioNoche().compareTo(b.precioNoche()) == 0
                && a.activa() == b.activa();
    }
}

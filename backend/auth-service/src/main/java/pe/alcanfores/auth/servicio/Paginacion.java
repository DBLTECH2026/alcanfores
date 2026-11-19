package pe.alcanfores.auth.servicio;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Normaliza la paginación que llega del front: tamaño entre 1 y 100 y orden solo por campos permitidos
 * (un {@code sort} desconocido no debe terminar en un error 500).
 */
final class Paginacion {

    static final int TAMANIO_MAXIMO = 100;

    private Paginacion() {
    }

    static Pageable sanear(Pageable p, Set<String> camposPermitidos, Sort porDefecto) {
        int tamanio = Math.min(Math.max(p.getPageSize(), 1), TAMANIO_MAXIMO);
        int pagina = Math.max(p.getPageNumber(), 0);
        Sort orden = Sort.by(p.getSort().stream().filter(o -> camposPermitidos.contains(o.getProperty())).toList());
        return PageRequest.of(pagina, tamanio, orden.isSorted() ? orden : porDefecto);
    }

    /** Patrón LIKE en minúsculas con los comodines del usuario escapados. */
    static String patronContiene(String texto) {
        String escapado = texto.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}

package pe.alcanfores.notificaciones.controlador;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.util.Set;

/** Arma el {@link Pageable} con {@code page}, {@code size} y {@code sort=campo,desc}, solo por campos permitidos. */
final class Paginacion {

    private Paginacion() {
    }

    static Pageable de(int page, int size, String sort, Set<String> permitidos, Sort porDefecto) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size, porDefecto);
        }
        String[] partes = sort.split(",");
        String campo = partes[0].trim();
        if (!permitidos.contains(campo)) {
            throw NegocioExcepcion.validacion("sort", "Solo se puede ordenar por: " + String.join(", ", permitidos) + ".");
        }
        Sort.Direction dir = partes.length > 1 && "asc".equalsIgnoreCase(partes[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, campo).and(Sort.by(Sort.Direction.DESC, "id")));
    }
}

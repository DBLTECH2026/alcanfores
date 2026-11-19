package pe.alcanfores.comun.util;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Respuesta paginada estándar: {@code {contenido, pagina, tamanio, totalElementos, totalPaginas}}. */
public record PaginaRespuesta<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaRespuesta<T> de(Page<E> pagina, Function<E, T> mapeo) {
        return new PaginaRespuesta<>(pagina.getContent().stream().map(mapeo).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    public static <T> PaginaRespuesta<T> de(Page<T> pagina) {
        return de(pagina, Function.identity());
    }
}

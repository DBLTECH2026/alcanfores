package pe.alcanfores.comun.seguridad;

/** Agrupa los filtros y manejadores que registra la autoconfiguración de {@code comun}. */
public record ComponentesSeguridad(JwtFiltroAutenticacion jwtFiltro, InternoFiltro internoFiltro,
                                   ManejadoresSeguridad manejadores) {
}

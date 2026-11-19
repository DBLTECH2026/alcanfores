package pe.alcanfores.gateway.filtro;

import org.springframework.core.Ordered;

/**
 * Orden de los filtros globales (todos son {@code WebFilter}, así también cubren rutas que no van a ningún servicio
 * como {@code /interno/**} o {@code /api/estado}). CORS va antes que los rechazos para que el navegador pueda leer
 * los 401/404/413/429.
 */
public final class OrdenFiltros {

    public static final int ID_SOLICITUD = Ordered.HIGHEST_PRECEDENCE;
    public static final int REGISTRO_ACCESO = ID_SOLICITUD + 1;
    public static final int CORS = ID_SOLICITUD + 2;
    public static final int RUTAS_INTERNAS = ID_SOLICITUD + 3;
    public static final int TAMANIO_CUERPO = ID_SOLICITUD + 4;
    public static final int LIMITE_SOLICITUDES = ID_SOLICITUD + 5;
    public static final int JWT_ADMIN = ID_SOLICITUD + 6;
    public static final int IP_CLIENTE = ID_SOLICITUD + 7;

    private OrdenFiltros() {
    }
}

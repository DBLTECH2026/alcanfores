package pe.alcanfores.auth.dto;

import java.util.List;

/** Valores distintos ya registrados, para llenar los combos de filtro del panel. */
public record OpcionesBitacora(List<String> servicios, List<String> acciones) {
}

package pe.alcanfores.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.catalogo.dto.CalendarioDtos.DiaEspecialRespuesta;
import pe.alcanfores.catalogo.dto.CanchaDetalleRespuesta;
import pe.alcanfores.catalogo.dto.CanchaResumenRespuesta;
import pe.alcanfores.catalogo.dto.ComplejoRespuesta;
import pe.alcanfores.catalogo.dto.ResenaPublicaRespuesta;
import pe.alcanfores.catalogo.dto.ResenaSolicitud;
import pe.alcanfores.catalogo.servicio.CalendarioServicio;
import pe.alcanfores.catalogo.servicio.CanchaServicio;
import pe.alcanfores.catalogo.servicio.ConfiguracionServicio;
import pe.alcanfores.catalogo.servicio.ResenaServicio;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.SeguridadUtil;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.PaginaRespuesta;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/catalogo")
@RequiredArgsConstructor
@Tag(name = "Catálogo público", description = "Datos del complejo, canchas, reseñas y calendario para el portal")
public class CatalogoPublicoControlador {

    private final CanchaServicio canchas;
    private final ResenaServicio resenas;
    private final ConfiguracionServicio configuracion;
    private final CalendarioServicio calendario;
    private final Validator validador;

    @GetMapping("/complejo")
    @Operation(summary = "Datos del complejo", description = "Contacto, ubicación, horario, reglas de reserva y datos de Yape/Plin.")
    public ComplejoRespuesta complejo() {
        return configuracion.complejo();
    }

    @GetMapping("/canchas")
    @Operation(summary = "Canchas activas", description = "Con foto principal y calificación promedio.")
    public List<CanchaResumenRespuesta> canchas() {
        return canchas.listarActivas();
    }

    @GetMapping("/canchas/{idOSlug}")
    @Operation(summary = "Detalle de una cancha", description = "Por id o slug: fotos, características y resumen de reseñas.")
    public CanchaDetalleRespuesta cancha(@Parameter(description = "Id numérico o slug, p. ej. cancha-1-futbol-5")
                                         @PathVariable String idOSlug) {
        return canchas.detallePublico(idOSlug);
    }

    @GetMapping("/canchas/{id}/resenas")
    @Operation(summary = "Reseñas publicadas de una cancha", description = "Paginadas; orden = recientes | mejor | peor.")
    public PaginaRespuesta<ResenaPublicaRespuesta> resenas(
            @PathVariable Long id,
            @RequestParam(defaultValue = "recientes") String orden,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página empieza en 0.") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "El tamaño de página va de 1 a 50.")
            @Max(value = 50, message = "El tamaño de página va de 1 a 50.") int size) {
        return resenas.listarPublicadas(id, orden, page, size);
    }

    @PostMapping("/canchas/{id}/resenas")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Dejar una reseña verificada",
            description = "Exige sesión de cliente (rol CLIENTE; el DNI sale del token) y el código de una reserva jugada de "
                    + "esa cancha. 401 sin sesión, 403 con token de administrador, 422 si la reserva no habilita a reseñar, "
                    + "409 si ya tiene reseña.")
    public ResenaPublicaRespuesta crearResena(@PathVariable Long id, @RequestBody ResenaSolicitud solicitud) {
        // La sesión se revisa ANTES que los campos: sin cuenta no tiene sentido corregir el formulario.
        UsuarioToken cliente = SeguridadUtil.actual().orElseThrow(() ->
                new NegocioExcepcion(401, "NO_AUTENTICADO", "Inicia sesión para calificar la cancha."));
        if (!cliente.esCliente() || cliente.dni() == null) {
            throw new NegocioExcepcion(403, "SIN_PERMISO", "Solo los clientes con cuenta pueden calificar una cancha.");
        }
        Set<ConstraintViolation<ResenaSolicitud>> errores = validador.validate(solicitud);
        if (!errores.isEmpty()) {
            throw new ConstraintViolationException(errores);
        }
        return resenas.crear(id, solicitud, cliente);
    }

    @GetMapping("/calendario")
    @Operation(summary = "Feriados y días no laborables", description = "Para pintar el calendario del portal: "
            + "[{fecha, tipo, nombre, canchas}] (canchas nulo = todas). Por defecto hoy … hoy + 60 días; máximo 366.")
    public List<DiaEspecialRespuesta> calendario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return calendario.entre(desde, hasta);
    }
}

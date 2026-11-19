package pe.alcanfores.catalogo.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.catalogo.cliente.ElegibilidadResena;
import pe.alcanfores.catalogo.cliente.ReservasCliente;
import pe.alcanfores.catalogo.dto.CalificacionRespuesta;
import pe.alcanfores.catalogo.dto.ResenaAdminRespuesta;
import pe.alcanfores.catalogo.dto.ResenaPublicaRespuesta;
import pe.alcanfores.catalogo.dto.ResenaSolicitud;
import pe.alcanfores.catalogo.dto.ResumenResenasRespuesta;
import pe.alcanfores.catalogo.mapeador.ResenaMapeador;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.EstadoResena;
import pe.alcanfores.catalogo.modelo.Resena;
import pe.alcanfores.catalogo.repositorio.CanchaRepositorio;
import pe.alcanfores.catalogo.repositorio.ResenaRepositorio;
import pe.alcanfores.catalogo.servicio.ResenaServicio;
import pe.alcanfores.catalogo.util.Calificaciones;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResenaServicioImpl implements ResenaServicio {

    static final String DUPLICADA = "Esta reserva ya tiene una reseña. ¡Gracias por contarnos tu experiencia!";
    static final String AUTOR_POR_DEFECTO = "Cliente verificado";

    private final ResenaRepositorio resenas;
    private final CanchaRepositorio canchas;
    private final ReservasCliente reservas;
    private final ResenaMapeador mapeador;
    private final Auditor auditor;

    /** Orden de las reseñas públicas (parámetro {@code orden}). */
    enum Orden {
        RECIENTES(Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id"))),
        MEJOR(Sort.by(Sort.Order.desc("puntuacion"), Sort.Order.desc("creadoEn"), Sort.Order.desc("id"))),
        PEOR(Sort.by(Sort.Order.asc("puntuacion"), Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));

        final Sort sort;

        Orden(Sort sort) {
            this.sort = sort;
        }

        static Orden de(String valor) {
            if (valor == null || valor.isBlank()) {
                return RECIENTES;
            }
            try {
                return valueOf(valor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw NegocioExcepcion.validacion("orden", "Ordena por «recientes», «mejor» o «peor».");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<ResenaPublicaRespuesta> listarPublicadas(Long idCancha, String orden, int pagina, int tamanio) {
        Orden o = Orden.de(orden);
        canchaActiva(idCancha);
        return PaginaRespuesta.de(resenas.findByCanchaIdAndEstado(idCancha, EstadoResena.PUBLICADA,
                PageRequest.of(pagina, tamanio, o.sort)), mapeador::publica);
    }

    @Override
    @Transactional
    public ResenaPublicaRespuesta crear(Long idCancha, ResenaSolicitud s) {
        Cancha cancha = canchaActiva(idCancha);
        String codigo = s.codigoReserva().trim().toUpperCase(Locale.ROOT);
        // Primero código + DNI (reservas): así el 409 «ya tiene reseña» no revela a un tercero que el código existe.
        ElegibilidadResena e = reservas.elegibleResena(codigo, s.dni().trim(), idCancha);
        if (!e.elegible()) {
            throw NegocioExcepcion.regla("RESENA_NO_ELEGIBLE", e.motivo() == null || e.motivo().isBlank()
                    ? "Esta reserva todavía no permite dejar una reseña." : e.motivo());
        }
        if (resenas.existsByCodigoReserva(codigo)) {
            throw NegocioExcepcion.conflicto("RESENA_DUPLICADA", DUPLICADA);
        }
        Resena r = new Resena();
        r.setCancha(cancha);
        r.setCodigoReserva(codigo);
        r.setAutor(autor(e.autor()));
        r.setPuntuacion(s.puntuacion());
        r.setComentario(Texto.limpiar(s.comentario()));
        r.setEstado(EstadoResena.PUBLICADA);
        try {
            resenas.saveAndFlush(r);
        } catch (DataIntegrityViolationException carrera) {
            // Dos envíos simultáneos de la misma reserva: el índice único decide y el segundo recibe el mismo 409.
            throw NegocioExcepcion.conflicto("RESENA_DUPLICADA", DUPLICADA);
        }
        auditor.registrarComo(r.getAutor(), "CLIENTE", "CREAR_RESENA", "resena", r.getId(),
                cancha.getNombre() + " · " + codigo + " · " + r.getPuntuacion() + " estrellas");
        return mapeador.publica(r);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeParaReserva(String codigoReserva) {
        return codigoReserva != null && resenas.existsByCodigoReserva(codigoReserva.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenResenasRespuesta resumen(Long idCancha) {
        Map<String, Long> distribucion = new LinkedHashMap<>();
        for (int estrellas = 5; estrellas >= 1; estrellas--) {
            distribucion.put(String.valueOf(estrellas), 0L);
        }
        long total = 0;
        long suma = 0;
        for (Object[] fila : resenas.contarPorPuntuacion(idCancha, EstadoResena.PUBLICADA)) {
            int estrellas = ((Number) fila[0]).intValue();
            long cantidad = ((Number) fila[1]).longValue();
            distribucion.merge(String.valueOf(estrellas), cantidad, Long::sum);
            total += cantidad;
            suma += (long) estrellas * cantidad;
        }
        return new ResumenResenasRespuesta(Calificaciones.promedio(suma, total), total, distribucion);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CalificacionRespuesta> calificacionesPorCancha() {
        Map<Long, CalificacionRespuesta> mapa = new HashMap<>();
        for (Object[] fila : resenas.calificacionPorCancha(EstadoResena.PUBLICADA)) {
            long suma = ((Number) fila[1]).longValue();
            long total = ((Number) fila[2]).longValue();
            mapa.put(((Number) fila[0]).longValue(), new CalificacionRespuesta(Calificaciones.promedio(suma, total), total));
        }
        return mapa;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<ResenaAdminRespuesta> listarAdmin(EstadoResena estado, Long idCancha, Integer puntuacion,
                                                             int pagina, int tamanio) {
        return PaginaRespuesta.de(resenas.buscar(estado, idCancha, puntuacion,
                PageRequest.of(pagina, tamanio, Orden.RECIENTES.sort)), mapeador::admin);
    }

    @Override
    @Transactional
    public ResenaAdminRespuesta cambiarEstado(Long id, EstadoResena estado) {
        Resena r = buscar(id);
        EstadoResena anterior = r.getEstado();
        r.setEstado(estado);
        if (anterior != estado) {
            auditor.registrar(estado == EstadoResena.OCULTA ? "OCULTAR_RESENA" : "PUBLICAR_RESENA", "resena", id,
                    r.getCancha().getNombre() + " · " + r.getAutor() + " · " + r.getPuntuacion() + " estrellas");
        }
        return mapeador.admin(r);
    }

    @Override
    @Transactional
    public ResenaAdminRespuesta responder(Long id, String respuesta) {
        Resena r = buscar(id);
        r.setRespuesta(Texto.limpiar(respuesta));
        r.setRespondidoEn(Tiempo.ahora());
        auditor.registrar("RESPONDER_RESENA", "resena", id, r.getCancha().getNombre() + " · " + r.getAutor());
        return mapeador.admin(r);
    }

    private Resena buscar(Long id) {
        return resenas.findById(id).orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos esa reseña."));
    }

    private Cancha canchaActiva(Long idCancha) {
        return canchas.findById(idCancha).filter(Cancha::estaActiva)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(CanchaServicioImpl.NO_ENCONTRADA));
    }

    private static String autor(String autor) {
        String limpio = Texto.limpiar(autor);
        if (limpio == null || limpio.isEmpty()) {
            return AUTOR_POR_DEFECTO;
        }
        return limpio.length() > 60 ? limpio.substring(0, 60).trim() : limpio;
    }
}

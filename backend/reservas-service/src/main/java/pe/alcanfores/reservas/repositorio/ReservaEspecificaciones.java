package pe.alcanfores.reservas.repositorio;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.alcanfores.reservas.modelo.Cliente;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.Turno;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filtros dinámicos del listado de reservas del panel. */
public final class ReservaEspecificaciones {

    private ReservaEspecificaciones() {
    }

    /** Todos los filtros salvo estado y estado de pago (sirve también para los conteos por pestaña). */
    public static Specification<Reserva> base(LocalDate fecha, LocalDate desde, LocalDate hasta, Long idCancha, Origen origen,
                                              String q) {
        return (root, query, cb) -> {
            Join<Reserva, Turno> t = root.join("turno");
            Join<Reserva, Cliente> c = root.join("cliente");
            List<Predicate> p = new ArrayList<>();
            if (fecha != null) {
                p.add(cb.equal(t.get("fecha"), fecha));
            }
            if (desde != null) {
                p.add(cb.greaterThanOrEqualTo(t.get("fecha"), desde));
            }
            if (hasta != null) {
                p.add(cb.lessThanOrEqualTo(t.get("fecha"), hasta));
            }
            if (idCancha != null) {
                p.add(cb.equal(t.get("idCancha"), idCancha));
            }
            if (origen != null) {
                p.add(cb.equal(root.get("origen"), origen));
            }
            if (q != null && !q.isBlank()) {
                String texto = q.trim().toLowerCase(Locale.ROOT);
                String patron = "%" + texto + "%";
                String digitos = texto.replaceAll("\\D", "");
                List<Predicate> o = new ArrayList<>();
                o.add(cb.like(cb.lower(root.get("codigoReserva")), patron));
                o.add(cb.like(cb.lower(cb.concat(cb.concat(c.get("nombres"), " "), c.get("apellidos"))), patron));
                if (!digitos.isEmpty()) {
                    o.add(cb.like(c.get("dni"), "%" + digitos + "%"));
                    o.add(cb.like(c.get("telefono"), "%" + digitos + "%"));
                }
                p.add(cb.or(o.toArray(Predicate[]::new)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public static Specification<Reserva> estado(EstadoReserva estado) {
        return (root, query, cb) -> estado == null ? cb.conjunction() : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Reserva> estadoPago(EstadoPago estadoPago) {
        return (root, query, cb) -> estadoPago == null ? cb.conjunction() : cb.equal(root.get("estadoPago"), estadoPago);
    }
}

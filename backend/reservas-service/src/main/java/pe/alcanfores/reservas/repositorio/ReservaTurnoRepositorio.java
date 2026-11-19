package pe.alcanfores.reservas.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.reservas.modelo.ReservaTurno;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReservaTurnoRepositorio extends JpaRepository<ReservaTurno, ReservaTurno.Clave> {

    /** Filas vigentes (reserva PENDIENTE o CONFIRMADA) de esos turnos, con su reserva y su cliente. */
    @Query("select rt from ReservaTurno rt join fetch rt.reserva r join fetch r.cliente "
            + "where rt.vigente = true and rt.turno.id in :ids")
    List<ReservaTurno> vigentesDeTurnos(@Param("ids") Collection<Long> idsTurno);

    boolean existsByTurnoIdAndVigenteTrue(Long idTurno);

    /** Turnos que alguna reserva (vigente o no) referencia: no se pueden borrar. */
    @Query("select distinct rt.turno.id from ReservaTurno rt where rt.turno.id in :ids")
    List<Long> referenciados(@Param("ids") Collection<Long> idsTurno);

    @Query("select distinct rt.reserva.codigoReserva from ReservaTurno rt where rt.vigente = true and rt.turno.fecha = :fecha")
    List<String> codigosVigentesEn(@Param("fecha") LocalDate fecha);

    @Query("select distinct rt.reserva.codigoReserva from ReservaTurno rt where rt.vigente = true and rt.turno.fecha = :fecha "
            + "and rt.turno.idCancha in :canchas")
    List<String> codigosVigentesEn(@Param("fecha") LocalDate fecha, @Param("canchas") Collection<Long> idsCancha);
}

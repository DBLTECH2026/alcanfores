package pe.alcanfores.pagos.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pe.alcanfores.comun.eventos.ClienteContacto;

/**
 * Fotografía de la reserva y del cliente al momento del pago: los comprobantes y eventos se arman con ella sin volver
 * a llamar a reservas-service (database-per-service).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InstantaneaReserva {

    @Column(name = "cliente_dni", nullable = false, length = 8)
    private String clienteDni;

    @Column(name = "cliente_nombres", nullable = false, length = 60)
    private String clienteNombres;

    @Column(name = "cliente_apellidos", nullable = false, length = 60)
    private String clienteApellidos;

    @Column(name = "cliente_telefono", length = 9)
    private String clienteTelefono;

    @Column(name = "cliente_correo", length = 100)
    private String clienteCorreo;

    /** «Alquiler de Cancha 2 · Fútbol 6 · vie 20/11/2026 7:00–8:00 p. m.» (o varios turnos: «… 9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos)») */
    @Column(name = "concepto", nullable = false, length = 160)
    private String concepto;

    @Column(name = "porcentaje_adelanto")
    private Integer porcentajeAdelanto;

    /** La misma fotografía con otro horario (la reserva se reprogramó): los datos del cliente no cambian. */
    public InstantaneaReserva conHorario(String nuevoConcepto, Integer nuevoPorcentajeAdelanto) {
        return new InstantaneaReserva(clienteDni, clienteNombres, clienteApellidos, clienteTelefono, clienteCorreo,
                nuevoConcepto, nuevoPorcentajeAdelanto);
    }

    public ClienteContacto contacto() {
        return new ClienteContacto(clienteDni, clienteNombres, clienteApellidos, clienteTelefono, clienteCorreo);
    }

    public String nombreCompleto() {
        return (clienteNombres + " " + clienteApellidos).trim();
    }
}

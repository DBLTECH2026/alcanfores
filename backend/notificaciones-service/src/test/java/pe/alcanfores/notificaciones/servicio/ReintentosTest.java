package pe.alcanfores.notificaciones.servicio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tarea de reintentos: FALLIDOS con menos de 3 intentos y PENDIENTES colgados; nunca las pruebas del panel. */
class ReintentosTest extends PruebaIntegracion {

    @Autowired
    private NotificacionServicio servicio;

    private Notificacion guardar(String evento, EstadoNotificacion estado, int intentos, LocalDateTime creado) {
        Notificacion n = new Notificacion(Datos.CODIGO, evento, CanalNotificacion.WHATSAPP, "51987654321", "Asunto", "*Hola*",
                List.of(), creado);
        for (int i = 0; i < intentos; i++) {
            n.registrarResultado(estado, "Evolution API respondió 500", creado);
        }
        return notificaciones.save(n);
    }

    @Test
    @DisplayName("Reintenta FALLIDOS con intentos < 3 y PENDIENTES de hace más de 5 min; respeta el resto")
    void reintenta() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 19, 10, 0);
        Datos.reloj(ahora);
        Notificacion fallida1 = guardar("reserva.confirmada", EstadoNotificacion.FALLIDO, 1, ahora.minusMinutes(5));
        Notificacion fallida2 = guardar("recordatorio", EstadoNotificacion.FALLIDO, 2, ahora.minusMinutes(10));
        Notificacion agotada = guardar("reserva.confirmada", EstadoNotificacion.FALLIDO, 3, ahora.minusMinutes(15));
        Notificacion prueba = guardar("whatsapp.prueba", EstadoNotificacion.FALLIDO, 1, ahora.minusMinutes(5));
        Notificacion colgada = guardar("reserva.registrada", EstadoNotificacion.PENDIENTE, 0, ahora.minusMinutes(6));
        Notificacion reciente = guardar("reserva.registrada", EstadoNotificacion.PENDIENTE, 0, ahora.minusMinutes(1));
        Notificacion enviada = guardar("reserva.registrada", EstadoNotificacion.ENVIADO, 1, ahora.minusMinutes(30));

        int reintentados = servicio.reintentarFallidas();

        assertThat(reintentados).isEqualTo(3);
        assertThat(estado(fallida1)).isEqualTo(EstadoNotificacion.SIMULADO);
        assertThat(notificaciones.findById(fallida1.getId()).orElseThrow().getIntentos()).isEqualTo(2);
        assertThat(estado(fallida2)).isEqualTo(EstadoNotificacion.SIMULADO);
        assertThat(estado(colgada)).isEqualTo(EstadoNotificacion.SIMULADO);
        assertThat(estado(agotada)).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(estado(prueba)).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(estado(reciente)).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(estado(enviada)).isEqualTo(EstadoNotificacion.ENVIADO);

        assertThat(servicio.reintentarFallidas()).isZero();
        Tiempo.restablecer();
    }

    private EstadoNotificacion estado(Notificacion n) {
        return notificaciones.findById(n.getId()).orElseThrow().getEstado();
    }
}

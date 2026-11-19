package pe.alcanfores.notificaciones.plantilla;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.servicio.impl.FabricaAvisos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModeloMensajesTest {

    @AfterEach
    void restablecer() {
        Tiempo.restablecer();
    }

    private static ModeloMensajes modelos(String frontUrl) {
        AlcanforesPropiedades p = new AlcanforesPropiedades();
        p.setFrontUrl(frontUrl);
        return new ModeloMensajes(p);
    }

    @Test
    @DisplayName("Enlaces del cliente con FRONT_URL (primera de la lista, sin barra final)")
    void enlaces() {
        ModeloMensajes m = modelos("https://reservas.alcanfores.pe/ , http://localhost:5186");

        assertThat(m.frontUrl()).isEqualTo("https://reservas.alcanfores.pe");
        assertThat(m.urlMiReserva("ALC-7K3M9P")).isEqualTo("https://reservas.alcanfores.pe/mi-reserva?codigo=ALC-7K3M9P");
        assertThat(m.urlResena(3L, "ALC-7K3M9P")).isEqualTo("https://reservas.alcanfores.pe/canchas/3?resena=ALC-7K3M9P");
        Map<String, Object> r = m.reserva(Datos.reserva(), Datos.complejo());
        assertThat(r.get("urlListo")).isEqualTo("https://reservas.alcanfores.pe/reservar/listo/ALC-7K3M9P");
        assertThat(r.get("urlReservar")).isEqualTo("https://reservas.alcanfores.pe/reservar");
    }

    @Test
    @DisplayName("Textos: duración, cancha con deporte, plazo y saludo")
    void textos() {
        assertThat(ModeloMensajes.duracionTexto(120)).isEqualTo("2 horas");
        assertThat(ModeloMensajes.duracionTexto(60)).isEqualTo("1 hora");
        assertThat(ModeloMensajes.duracionTexto(75)).isEqualTo("1 hora y 15 minutos");
        assertThat(ModeloMensajes.duracionTexto(45)).isEqualTo("45 minutos");
        assertThat(ModeloMensajes.duracionTexto(121)).isEqualTo("2 horas y 1 minuto");
        assertThat(ModeloMensajes.canchaTexto("Cancha 1", "FUTBOL_5")).isEqualTo("Cancha 1 · Fútbol 5");
        assertThat(ModeloMensajes.canchaTexto("Cancha 1 · Fútbol 5", "FUTBOL_5")).isEqualTo("Cancha 1 · Fútbol 5");
        assertThat(ModeloMensajes.canchaTexto("Losa multiusos", "MULTIUSO")).isEqualTo("Losa multiusos · Vóley y básquet");
        assertThat(ModeloMensajes.canchaTexto(null, "VOLEY")).isEqualTo("Vóley");
        assertThat(ModeloMensajes.deporteTexto("BASQUET")).isEqualTo("Básquet");
        assertThat(ModeloMensajes.deporteTexto("OTRO")).isEqualTo("OTRO");

        Datos.reloj(LocalDateTime.of(2026, 11, 19, 18, 0));
        assertThat(ModeloMensajes.plazoTexto(LocalDateTime.of(2026, 11, 19, 18, 20))).isEqualTo("6:20 p. m. de hoy");
        assertThat(ModeloMensajes.plazoTexto(LocalDateTime.of(2026, 11, 20, 9, 5))).isEqualTo("viernes 20 de noviembre, 9:05 a. m.");
    }

    @Test
    @DisplayName("Montos y saldo de la reserva; saludo sin nombre")
    void montos() {
        DatosReserva r = new DatosReserva(1L, "ALC-7K3M9P", 1L, "Cancha 1", "FUTBOL_5", Datos.FECHA, Datos.HORA,
                Datos.HORA.plusHours(1), new BigDecimal("80"), new BigDecimal("40"), new BigDecimal("40"), null);
        Map<String, Object> m = modelos("http://localhost:5186").reserva(r, Datos.complejo());

        assertThat(m).containsEntry("montoTotal", "S/ 80.00").containsEntry("montoPagado", "S/ 40.00")
                .containsEntry("saldo", "S/ 40.00").containsEntry("haySaldo", true).containsEntry("hayPago", true)
                .containsEntry("saludo", "Hola").containsEntry("fecha", "Viernes 20 de noviembre de 2026")
                .containsEntry("fechaCorta", "20/11/2026").containsEntry("horario", "7:00 p. m. a 8:00 p. m.");
    }

    @Test
    @DisplayName("Número de WhatsApp: normaliza o descarta celulares no válidos")
    void numeroWhatsapp() {
        assertThat(FabricaAvisos.numeroWhatsapp("+51 987 654 321")).isEqualTo("51987654321");
        assertThat(FabricaAvisos.numeroWhatsapp("987-654-321")).isEqualTo("51987654321");
        assertThat(FabricaAvisos.numeroWhatsapp("012345678")).isNull();
        assertThat(FabricaAvisos.numeroWhatsapp(null)).isNull();
    }

    @Test
    @DisplayName("Texto legible de cada evento para el panel")
    void eventoTexto() {
        assertThat(TipoAviso.textoDe("reserva.confirmada")).isEqualTo("Reserva confirmada");
        assertThat(TipoAviso.textoDe("recordatorio")).isEqualTo("Recordatorio del partido");
        assertThat(TipoAviso.textoDe("otro.evento")).isEqualTo("otro.evento");
    }
}

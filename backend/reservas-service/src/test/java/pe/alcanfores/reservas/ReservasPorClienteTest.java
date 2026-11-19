package pe.alcanfores.reservas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /interno/reservas/por-cliente?dni&telefono: Canchita reconoce al cliente que escribe desde el celular registrado.
 * Solo devuelve las reservas HECHAS con ese celular (sin revelar nada si no calza) y desde hace 30 días en adelante.
 */
class ReservasPorClienteTest extends PruebaIntegracion {

    static final String DNI = "70123321";
    private String hace10;
    private String manana;
    private String enTresDias;

    private ResultActions porCliente(String dni, String telefono) throws Exception {
        return mvc.perform(get("/interno/reservas/por-cliente").param("dni", dni).param("telefono", telefono)
                .header("X-Interno-Token", TOKEN_INTERNO));
    }

    /** Reservas del mismo DNI (celular +51 987 654 321): hace 40 días, hace 10, mañana (2 turnos) y en 3 días (cancelada). */
    @BeforeEach
    void reservasDelCliente() throws Exception {
        fijarReloj(AHORA.minusDays(41));
        reservarWeb(turno(1, HOY.minusDays(40), 19).getId(), DNI);
        fijarReloj(AHORA.minusDays(11));
        hace10 = reservarWeb(turno(2, HOY.minusDays(10), 20).getId(), DNI).get("codigoReserva").asText();
        fijarReloj(AHORA);
        enTresDias = reservarWeb(turno(1, HOY.plusDays(3), 18).getId(), DNI).get("codigoReserva").asText();
        mvc.perform(post("/api/reservas/" + enTresDias + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"" + DNI + "\",\"motivo\":\"Cambio de planes del equipo\"}")).andExpect(status().isOk());
        manana = reservarWebVarios(List.of(turno(3, HOY.plusDays(1), 19).getId(), turno(3, HOY.plusDays(1), 20).getId()), DNI)
                .get("codigoReserva").asText();
        reservarWeb(turno(4, HOY.plusDays(1), 19).getId(), "70123322");
    }

    @Test
    void conElCelularRegistradoDevuelveSusReservasDesdeHace30DiasPorFechaYHora() throws Exception {
        for (String telefono : new String[]{"987654321", "51987654321", "+51 987 654 321"}) {
            porCliente(DNI, telefono)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].codigo").value(hace10))
                    .andExpect(jsonPath("$[0].fecha").value(HOY.minusDays(10).toString()))
                    .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$[1].codigo").value(manana))
                    .andExpect(jsonPath("$[1].cancha").value("Cancha 3 · Fútbol 7"))
                    .andExpect(jsonPath("$[1].fecha").value(HOY.plusDays(1).toString()))
                    .andExpect(jsonPath("$[1].horaInicio").value("19:00"))
                    .andExpect(jsonPath("$[1].horaFin").value("21:00"))
                    .andExpect(jsonPath("$[1].horarioTexto").value("7:00–9:00 p. m."))
                    .andExpect(jsonPath("$[1].cantidadTurnos").value(2))
                    .andExpect(jsonPath("$[1].turnos.length()").value(2))
                    .andExpect(jsonPath("$[1].estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$[1].estadoPago").value("SIN_PAGO"))
                    .andExpect(jsonPath("$[1].montoTotal").value(240.0))
                    .andExpect(jsonPath("$[1].pagado").value(0.0))
                    .andExpect(jsonPath("$[1].saldo").value(240.0))
                    .andExpect(jsonPath("$[2].codigo").value(enTresDias))
                    .andExpect(jsonPath("$[2].estado").value("CANCELADA"))
                    .andExpect(jsonPath("$[2].saldo").value(0.0));
        }
    }

    @Test
    void siElCelularNoCalzaOElDniNoExisteLaListaVieneVacia() throws Exception {
        porCliente(DNI, "912345678").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        porCliente("80000001", "987654321").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validaDniYCelularYSoloAtiendeAOtrosServicios() throws Exception {
        porCliente("7012332", "987654321")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("dni"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El DNI debe tener 8 dígitos."));
        porCliente(DNI, "887654321")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("telefono"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa un celular peruano de 9 dígitos que empiece con 9."));
        mvc.perform(get("/interno/reservas/por-cliente").param("dni", DNI).header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/interno/reservas/por-cliente").param("dni", DNI).param("telefono", "987654321"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/interno/reservas/por-cliente").param("dni", DNI).param("telefono", "987654321")
                        .header("X-Interno-Token", "otro-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void otraReservaDelMismoDniConOtroCelularNoExponeLasReservasAnteriores() throws Exception {
        // Alguien reserva por la web con el DNI del cliente y SU propio celular (el cliente.telefono pasa a ser el suyo).
        String respuesta = mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idsTurno": [%d], "cliente": {"dni": "%s", "nombres": "otro", "apellidos": "nombre",
                         "telefono": "912 345 678", "correo": "otro@example.com"},
                         "metodoPago": "YAPE", "aceptaTerminos": true, "aceptaPrivacidad": true}
                        """.formatted(turno(2, HOY.plusDays(2), 19).getId(), DNI)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ajena = json.readTree(respuesta).get("codigoReserva").asText();

        // Con su celular solo ve la reserva que hizo con él.
        porCliente(DNI, "912345678")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value(ajena));
        // El dueño sigue viendo las suyas desde su número (y no la ajena).
        porCliente(DNI, "+51 987 654 321")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].codigo").value(manana));

        // Los avisos de cada reserva salen al celular con el que se hizo.
        ArgumentCaptor<EventoDominio> registradas = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.RESERVA_REGISTRADA), registradas.capture());
        assertThat(registradas.getAllValues().stream().map(ReservaRegistradaEvento.class::cast)
                .filter(e -> e.reserva().codigoReserva().equals(ajena)).findFirst().orElseThrow()
                .reserva().cliente().telefono()).isEqualTo("912345678");
        assertThat(jdbc.queryForObject("SELECT telefono_contacto FROM reserva WHERE codigo_reserva = ?", String.class, manana))
                .isEqualTo("987654321");
        mvc.perform(get("/interno/reservas/" + manana).header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.telefono").value("987654321"));
    }
}

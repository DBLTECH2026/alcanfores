package pe.alcanfores.reservas.cliente;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.eventos.HorarioDia;
import pe.alcanfores.comun.util.Horarios;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.servicio.impl.ComplejoServicioImpl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Copia en memoria del catálogo (canchas con horario y calendario) y reglas de horario de cada cancha. */
class ComplejoServicioImplTest {

    private final CatalogoCliente catalogo = mock(CatalogoCliente.class);

    @AfterEach
    void restablecer() {
        Tiempo.restablecer();
    }

    @Test
    void mientrasCatalogoNoRespondaLaCopiaEsLaSemillaYNoCuentaComoLeida() {
        Tiempo.fijar(Clock.fixed(java.time.Instant.parse("2026-11-20T15:40:00Z"), Tiempo.ZONA));
        when(catalogo.configuracion()).thenThrow(new IllegalStateException("caído"));
        when(catalogo.canchas()).thenThrow(new IllegalStateException("caído"));
        when(catalogo.calendario(any(), any())).thenThrow(new IllegalStateException("caído"));
        ComplejoServicioImpl complejo = new ComplejoServicioImpl(catalogo);
        assertThat(complejo.refrescar()).isEmpty();
        assertThat(complejo.refrescarCalendario()).isEmpty();
        assertThat(complejo.catalogoLeido()).isFalse();
        assertThat(complejo.canchas()).hasSize(4);
    }

    @Test
    void conCanchasYCalendarioLeidosDiceQueCambio() {
        Tiempo.fijar(Clock.fixed(java.time.Instant.parse("2026-11-20T15:40:00Z"), Tiempo.ZONA));
        LocalDate navidad = LocalDate.of(2026, 12, 25);
        when(catalogo.configuracion()).thenReturn(Map.of());
        when(catalogo.canchas()).thenReturn(CanchaCatalogo.semilla());
        when(catalogo.calendario(any(), any())).thenReturn(List.of(
                new DiaEspecial(1L, navidad, "FERIADO", "Navidad", null),
                new DiaEspecial(2L, null, "FERIADO", "Sin fecha", null)));
        ComplejoServicioImpl complejo = new ComplejoServicioImpl(catalogo);
        assertThat(complejo.refrescar()).isEmpty();
        assertThat(complejo.refrescarCalendario()).containsExactly(navidad);
        assertThat(complejo.catalogoLeido()).isTrue();
        // Mismo calendario (aunque cambie el id): no hay cambios. Si desaparece, la fecha cambia.
        when(catalogo.calendario(any(), any())).thenReturn(List.of(new DiaEspecial(9L, navidad, "FERIADO", "Navidad", List.of())));
        assertThat(complejo.refrescarCalendario()).isEmpty();
        when(catalogo.calendario(any(), any())).thenReturn(null);
        assertThat(complejo.refrescarCalendario()).containsExactly(navidad);
        assertThat(complejo.diaEspecial(navidad)).isEmpty();
        assertThat(complejo.diaEspecial(null)).isEmpty();
    }

    @Test
    void reglasDeHorarioDeLaCancha() {
        ParametrosComplejo p = ParametrosComplejo.porDefecto();
        CanchaCatalogo sinHorario = CanchaCatalogo.semilla().get(0);
        assertThat(sinHorario.tramos(DayOfWeek.MONDAY, p)).hasSize(15);
        assertThat(sinHorario.recargo()).isEqualByComparingTo("10.00");
        assertThat(sinHorario.cerradaEnFeriado()).isFalse();
        assertThat(sinHorario.mismosDatos(null)).isFalse();

        List<HorarioDia> dias = List.of(new HorarioDia(1, true, LocalTime.of(8, 0), LocalTime.MIDNIGHT),
                new HorarioDia(2, true, LocalTime.of(8, 0), LocalTime.of(23, 59)),
                new HorarioDia(3, false, null, null), new HorarioDia(4, true, null, LocalTime.of(22, 0)));
        CanchaCatalogo c = new CanchaCatalogo(1L, "Cancha 1", "FUTBOL_5", new BigDecimal("60"), new BigDecimal("80"), "ACTIVA",
                120, LocalTime.of(19, 0), dias, "CERRADA", new BigDecimal("5"));
        assertThat(c.tramos(DayOfWeek.MONDAY, p)).hasSize(8).last().extracting(Horarios.Tramo::fin).isEqualTo(Horarios.FIN_DEL_DIA);
        assertThat(c.tramos(DayOfWeek.TUESDAY, p)).hasSize(8);
        assertThat(c.tramos(DayOfWeek.WEDNESDAY, p)).isEmpty();
        assertThat(c.tramos(DayOfWeek.THURSDAY, p)).isEmpty();
        // Un día que no vino en la lista usa el horario del complejo.
        assertThat(c.tramos(DayOfWeek.SUNDAY, p)).hasSize(7);
        assertThat(c.cerradaEnFeriado()).isTrue();
        assertThat(c.recargo()).isEqualByComparingTo("5.00");
        assertThat(c.mismosDatos(c)).isTrue();
        assertThat(c.mismosDatos(sinHorario)).isFalse();
    }
}

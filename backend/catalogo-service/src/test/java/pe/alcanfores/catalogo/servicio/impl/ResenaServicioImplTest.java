package pe.alcanfores.catalogo.servicio.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pe.alcanfores.catalogo.cliente.ElegibilidadResena;
import pe.alcanfores.catalogo.cliente.ReservasCliente;
import pe.alcanfores.catalogo.dto.ResenaPublicaRespuesta;
import pe.alcanfores.catalogo.dto.ResenaSolicitud;
import pe.alcanfores.catalogo.dto.ResumenResenasRespuesta;
import pe.alcanfores.catalogo.mapeador.ResenaMapeador;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.EstadoCancha;
import pe.alcanfores.catalogo.modelo.EstadoResena;
import pe.alcanfores.catalogo.modelo.Resena;
import pe.alcanfores.catalogo.repositorio.CanchaRepositorio;
import pe.alcanfores.catalogo.repositorio.ResenaRepositorio;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResenaServicioImplTest {

    @Mock
    ResenaRepositorio resenas;
    @Mock
    CanchaRepositorio canchas;
    @Mock
    ReservasCliente reservas;
    @Mock
    Auditor auditor;

    ResenaServicioImpl servicio;
    Cancha cancha;

    @BeforeEach
    void preparar() {
        servicio = new ResenaServicioImpl(resenas, canchas, reservas, new ResenaMapeador(), auditor);
        cancha = new Cancha();
        cancha.setId(1L);
        cancha.setNombre("Cancha 1 · Fútbol 5");
        cancha.setEstado(EstadoCancha.ACTIVA);
    }

    static Object[] fila(Object... valores) {
        return valores;
    }

    @Test
    void resumenCalculaPromedioConUnDecimalYDistribucionCompleta() {
        when(resenas.contarPorPuntuacion(1L, EstadoResena.PUBLICADA))
                .thenReturn(List.of(fila(5, 3L), fila(4, 1L), fila(2, 1L)));
        ResumenResenasRespuesta r = servicio.resumen(1L);
        assertThat(r.total()).isEqualTo(5);
        assertThat(r.promedio()).isEqualByComparingTo("4.2"); // 21 / 5
        assertThat(r.distribucion()).containsExactly(
                java.util.Map.entry("5", 3L), java.util.Map.entry("4", 1L), java.util.Map.entry("3", 0L),
                java.util.Map.entry("2", 1L), java.util.Map.entry("1", 0L));
    }

    @Test
    void resumenRedondeaHaciaArribaLaMitad() {
        when(resenas.contarPorPuntuacion(1L, EstadoResena.PUBLICADA)).thenReturn(List.of(fila(5, 1L), fila(4, 3L)));
        assertThat(servicio.resumen(1L).promedio()).isEqualByComparingTo("4.3"); // 17 / 4 = 4.25
    }

    @Test
    void resumenSinResenasEsCero() {
        when(resenas.contarPorPuntuacion(1L, EstadoResena.PUBLICADA)).thenReturn(List.of());
        ResumenResenasRespuesta r = servicio.resumen(1L);
        assertThat(r.total()).isZero();
        assertThat(r.promedio()).isEqualByComparingTo("0.0");
        assertThat(r.distribucion()).hasSize(5).containsValues(0L);
    }

    @Test
    void calificacionesPorCanchaAgrupaSumaYTotal() {
        when(resenas.calificacionPorCancha(EstadoResena.PUBLICADA)).thenReturn(List.of(fila(1L, 22L, 5L), fila(4L, 17L, 4L)));
        var mapa = servicio.calificacionesPorCancha();
        assertThat(mapa.get(1L).promedio()).isEqualByComparingTo("4.4");
        assertThat(mapa.get(4L).total()).isEqualTo(4);
    }

    @Test
    void crearConReservaElegibleGuardaConElAutorDeReservasYAudita() {
        when(canchas.findById(1L)).thenReturn(Optional.of(cancha));
        when(resenas.existsByCodigoReserva("ALC-QW3RT7")).thenReturn(false);
        when(reservas.elegibleResena("ALC-QW3RT7", "45678912", 1L)).thenReturn(new ElegibilidadResena(true, null, "Diego R."));
        when(resenas.saveAndFlush(any(Resena.class))).thenAnswer(i -> i.getArgument(0));

        ResenaPublicaRespuesta r = servicio.crear(1L,
                new ResenaSolicitud(" alc-qw3rt7 ", "45678912", 5, "Muy  buena   cancha, volveremos."));

        assertThat(r.autor()).isEqualTo("Diego R.");
        assertThat(r.comentario()).isEqualTo("Muy buena cancha, volveremos.");
        assertThat(r.verificada()).isTrue();
        verify(auditor).registrarComo(org.mockito.ArgumentMatchers.eq("Diego R."), org.mockito.ArgumentMatchers.eq("CLIENTE"),
                org.mockito.ArgumentMatchers.eq("CREAR_RESENA"), anyString(), any(), anyString());
    }

    @Test
    void sinAutorSeUsaClienteVerificado() {
        when(canchas.findById(1L)).thenReturn(Optional.of(cancha));
        when(reservas.elegibleResena(anyString(), anyString(), any())).thenReturn(new ElegibilidadResena(true, null, "  "));
        when(resenas.saveAndFlush(any(Resena.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(servicio.crear(1L, new ResenaSolicitud("ALC-QW3RT7", "45678912", 4, "Todo bien en la cancha.")).autor())
                .isEqualTo("Cliente verificado");
    }

    @Test
    void duplicadaSeRespondeSoloDespuesDeComprobarCodigoYDni() {
        when(canchas.findById(1L)).thenReturn(Optional.of(cancha));
        when(reservas.elegibleResena("ALC-QW3RT7", "45678912", 1L)).thenReturn(new ElegibilidadResena(true, null, "Carla M."));
        when(resenas.existsByCodigoReserva("ALC-QW3RT7")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(1L, new ResenaSolicitud("ALC-QW3RT7", "45678912", 4, "Todo bien en la cancha.")))
                .isInstanceOf(NegocioExcepcion.class)
                .satisfies(e -> assertThat(((NegocioExcepcion) e).getCodigo()).isEqualTo("RESENA_DUPLICADA"));
    }

    @Test
    void conDniAjenoNoSeRevisaSiYaHayResena() {
        when(canchas.findById(1L)).thenReturn(Optional.of(cancha));
        when(reservas.elegibleResena("ALC-QW3RT7", "99019999", 1L))
                .thenReturn(new ElegibilidadResena(false, "No encontramos una reserva con ese código y DNI.", null));
        assertThatThrownBy(() -> servicio.crear(1L, new ResenaSolicitud("ALC-QW3RT7", "99019999", 4, "Todo bien en la cancha.")))
                .isInstanceOf(NegocioExcepcion.class)
                .satisfies(e -> assertThat(((NegocioExcepcion) e).getCodigo()).isEqualTo("RESENA_NO_ELEGIBLE"));
        verify(resenas, never()).existsByCodigoReserva(anyString());
    }

    @Test
    void existeParaReservaNormalizaElCodigo() {
        when(resenas.existsByCodigoReserva("ALC-QW3RT7")).thenReturn(true);
        assertThat(servicio.existeParaReserva(" alc-qw3rt7 ")).isTrue();
        assertThat(servicio.existeParaReserva(null)).isFalse();
    }

    @Test
    void noElegibleSinMotivoUsaUnMensajeAmable() {
        when(canchas.findById(1L)).thenReturn(Optional.of(cancha));
        when(reservas.elegibleResena(anyString(), anyString(), any())).thenReturn(new ElegibilidadResena(false, null, null));
        assertThatThrownBy(() -> servicio.crear(1L, new ResenaSolicitud("ALC-QW3RT7", "45678912", 4, "Todo bien en la cancha.")))
                .isInstanceOf(NegocioExcepcion.class)
                .hasMessage("Esta reserva todavía no permite dejar una reseña.")
                .satisfies(e -> assertThat(((NegocioExcepcion) e).getStatus()).isEqualTo(422));
    }

    @Test
    void envioSimultaneoTerminaEnElMismo409() {
        when(canchas.findById(1L)).thenReturn(Optional.of(cancha));
        when(reservas.elegibleResena(anyString(), anyString(), any())).thenReturn(new ElegibilidadResena(true, null, "Ana P."));
        when(resenas.saveAndFlush(any(Resena.class))).thenThrow(new DataIntegrityViolationException("uq_resena_codigo_reserva"));
        assertThatThrownBy(() -> servicio.crear(1L, new ResenaSolicitud("ALC-QW3RT7", "45678912", 4, "Todo bien en la cancha.")))
                .satisfies(e -> assertThat(((NegocioExcepcion) e).getStatus()).isEqualTo(409));
    }

    @Test
    void canchaInactivaNoRecibeResenas() {
        cancha.setEstado(EstadoCancha.INACTIVA);
        when(canchas.findById(1L)).thenReturn(Optional.of(cancha));
        assertThatThrownBy(() -> servicio.crear(1L, new ResenaSolicitud("ALC-QW3RT7", "45678912", 4, "Todo bien en la cancha.")))
                .satisfies(e -> assertThat(((NegocioExcepcion) e).getStatus()).isEqualTo(404));
    }

    @Test
    void ordenDesconocidoEsErrorDeValidacion() {
        assertThatThrownBy(() -> servicio.listarPublicadas(1L, "azar", 0, 10))
                .satisfies(e -> assertThat(((NegocioExcepcion) e).getErrores().get(0).campo()).isEqualTo("orden"));
        assertThat(ResenaServicioImpl.Orden.de(null)).isEqualTo(ResenaServicioImpl.Orden.RECIENTES);
        assertThat(ResenaServicioImpl.Orden.de(" Mejor ")).isEqualTo(ResenaServicioImpl.Orden.MEJOR);
    }
}

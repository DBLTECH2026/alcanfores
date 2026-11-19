package pe.alcanfores.pagos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.PublicadorEventos;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.cliente.CatalogoCliente;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente;
import pe.alcanfores.pagos.cliente.ReservaInterna;
import pe.alcanfores.pagos.cliente.ReservasCliente;
import pe.alcanfores.pagos.config.PagosPropiedades;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Contexto real (MariaDB {@code alcanfores_pagos_test} + Flyway, seguridad de {@code comun}, servidor en puerto
 * aleatorio para que la pasarela beta pueda llamar a su webhook) con los demás microservicios, Mercado Pago y
 * RabbitMQ simulados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "alcanfores.pagos.mercadopago.access-token=TEST-0000-token-de-pruebas",
        "alcanfores.pagos.mercadopago.webhook-secreto=secreto-webhook-mp-pruebas"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class IntegracionBase {

    public static final String CODIGO = "ALC-7K3M9P";
    public static final String DNI = "70123321";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected JwtServicio jwt;
    @Autowired
    protected PagosPropiedades propiedades;

    @MockitoBean
    protected ReservasCliente reservas;
    @MockitoBean
    protected CatalogoCliente catalogo;
    @MockitoBean
    protected MercadoPagoCliente mercadoPago;
    @MockitoBean
    protected PublicadorEventos publicador;
    @MockitoBean
    protected RabbitTemplate rabbit;

    @BeforeEach
    void limpiarBase() {
        jdbc.update("DELETE FROM comprobante WHERE id_referencia IS NOT NULL");
        jdbc.update("DELETE FROM comprobante");
        jdbc.update("DELETE FROM datos_facturacion");
        jdbc.update("DELETE FROM intento_pago");
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM webhook_evento");
        jdbc.update("DELETE FROM pasarela_transaccion");
        jdbc.update("DELETE FROM evento_procesado");
        jdbc.update("UPDATE serie SET ultimo_correlativo = 0");
        when(catalogo.configuracion()).thenReturn(Map.of("yape_numero", "987654321", "adelanto_pct", "50"));
        when(reservas.buscar(anyString())).thenReturn(Optional.empty());
    }

    @AfterEach
    void restaurarMercadoPago() {
        propiedades.getMercadopago().setAccessToken("TEST-0000-token-de-pruebas");
    }

    protected String tokenAdmin() {
        return "Bearer " + jwt.generar(new UsuarioToken(1L, "rhuaman", "Rosa Huamán", UsuarioToken.ADMIN));
    }

    protected String tokenRecepcion() {
        return "Bearer " + jwt.generar(new UsuarioToken(2L, "mtorres", "Marco Torres", UsuarioToken.RECEPCION));
    }

    /** Reserva de la Cancha 2 (S/ 90, adelanto S/ 45) para pasado mañana a las 7:00 p. m. */
    protected static ReservaInterna reserva(String codigo, String estado, String estadoPago, String pagado) {
        LocalDate fecha = Tiempo.hoy().plusDays(2);
        BigDecimal total = new BigDecimal("90.00");
        BigDecimal montoPagado = new BigDecimal(pagado);
        return new ReservaInterna(10L, codigo, 2L, "Cancha 2", "FUTBOL_6", fecha, LocalTime.of(19, 0), LocalTime.of(20, 0),
                total, new BigDecimal("45.00"), montoPagado,
                new ClienteContacto(DNI, "Diego Alonso", "Ramos Quispe", "987654321", "diego.ramos@correo.pe"),
                estado, estadoPago, total.subtract(montoPagado), LocalDateTime.of(fecha, LocalTime.of(19, 0)));
    }

    protected void reservaDisponible(ReservaInterna r) {
        when(reservas.buscar(r.codigoReserva())).thenReturn(Optional.of(r));
    }

    /** PNG real del tamaño indicado (para simular la captura de Yape/Plin). */
    protected static byte[] png(int ancho, int alto) throws IOException {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(91, 44, 131));
        g.fillRect(0, 0, ancho, alto / 3);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", salida);
        return salida.toByteArray();
    }
}

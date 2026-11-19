package pe.alcanfores.pagos.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pe.alcanfores.pagos.modelo.TipoComprobante;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Regla de factura del SPEC §3 (RUC 10/20 válido, razón social y dirección fiscal) y validaciones de pago. */
class FacturaCompletaTest {

    static jakarta.validation.ValidatorFactory fabrica;
    static Validator validador;

    @BeforeAll
    static void iniciar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    static Map<String, String> errores(Object o) {
        Set<? extends ConstraintViolation<?>> v = validador.validate(o);
        return v.stream().collect(Collectors.toMap(c -> c.getPropertyPath().toString(), ConstraintViolation::getMessage,
                (a, b) -> a));
    }

    @Test
    void boletaNoPideRuc() {
        assertThat(errores(DatosComprobanteSolicitud.boleta())).isEmpty();
    }

    @Test
    void facturaCompletaEsValida() {
        assertThat(errores(new DatosComprobanteSolicitud(TipoComprobante.FACTURA, "20000000001",
                "Los Alcanfores Sport E.I.R.L.", "Carretera Central km 11.5"))).isEmpty();
        assertThat(errores(new DatosComprobanteSolicitud(TipoComprobante.FACTURA,
                "1070123321" + pe.alcanfores.comun.validacion.Documentos.digitoVerificadorRuc("1070123321"),
                "Diego Ramos Quispe", "Av. Lima 123"))).isEmpty();
    }

    @Test
    void rucConDigitoVerificadorMalo() {
        assertThat(errores(new DatosComprobanteSolicitud(TipoComprobante.FACTURA, "20612345678", "Empresa SAC", "Av. Lima 123")))
                .containsExactly(Map.entry("ruc", "El RUC no es válido."));
    }

    @Test
    void rucValidoPeroNoDeEmpresaNiPersonaConNegocio() {
        String ruc15 = "15" + "00000000";
        ruc15 = ruc15 + pe.alcanfores.comun.validacion.Documentos.digitoVerificadorRuc(ruc15);
        assertThat(errores(new DatosComprobanteSolicitud(TipoComprobante.FACTURA, ruc15, "Empresa SAC", "Av. Lima 123")))
                .containsExactly(Map.entry("ruc", FacturaCompleta.MENSAJE));
    }

    @Test
    void facturaSinRucNiRazonSocialNiDireccion() {
        assertThat(errores(new DatosComprobanteSolicitud(TipoComprobante.FACTURA, null, "  ", "Av")))
                .containsEntry("ruc", FacturaCompleta.MENSAJE)
                .containsEntry("razonSocial", FacturaCompleta.MENSAJE)
                .containsEntry("direccionFiscal", FacturaCompleta.MENSAJE_DIRECCION);
    }

    @Test
    void tipoObligatorioYNoNotaDeCredito() {
        assertThat(errores(new DatosComprobanteSolicitud(null, null, null, null))).containsKey("tipo");
        assertThat(errores(new DatosComprobanteSolicitud(TipoComprobante.NOTA_CREDITO, null, null, null)))
                .containsEntry("tipo", FacturaCompleta.MENSAJE_TIPO);
    }

    @Test
    void formularioYCajaConMensajesDelSpec() {
        assertThat(errores(new FormularioComprobante("ALC-I0O1AA", "1234567", "EFECTIVO", "12345", null)))
                .containsEntry("codigoReserva", Mensajes.CODIGO)
                .containsEntry("dni", Mensajes.DNI)
                .containsEntry("metodo", Mensajes.METODO)
                .containsEntry("codigoOperacion", Mensajes.OPERACION);
        assertThat(errores(new SolicitudCaja("ALC-7K3M9P", "SALDO", "EFECTIVO", new java.math.BigDecimal("10.123"), null, null)))
                .containsEntry("monto", Mensajes.MONTO_DECIMALES);
        assertThat(errores(new SolicitudRechazo("muy corto"))).containsEntry("observacion", Mensajes.MOTIVO);
        assertThat(errores(new SolicitudAnulacion(null))).containsEntry("motivo", Mensajes.MOTIVO);
    }
}

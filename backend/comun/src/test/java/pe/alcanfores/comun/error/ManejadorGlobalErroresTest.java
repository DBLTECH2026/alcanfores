package pe.alcanfores.comun.error;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.util.TypeInformation;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.comun.validacion.Dni;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.SQLException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba los dos @RestControllerAdvice de {@code comun} con MockMvc standalone: cada excepción debe salir con el
 * cuerpo {@code {timestamp, status, codigo, mensaje, ruta, errores}} y mensajes en español.
 */
class ManejadorGlobalErroresTest {

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        // El global va primero a propósito: @Order(HIGHEST_PRECEDENCE) debe hacer que el de infraestructura gane.
        mvc = MockMvcBuilders.standaloneSetup(new ControladorPrueba())
                .setControllerAdvice(new ManejadorGlobalErrores(), new ManejadorErroresInfraestructura())
                .build();
    }

    private ResultActions esperarError(ResultActions r, int status, String codigo, String ruta) throws Exception {
        return r.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.ruta").value(ruta))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.mensaje", notNullValue()))
                .andExpect(jsonPath("$.errores", notNullValue()));
    }

    // ---------- Negocio ----------

    @Test
    void negocioSinErroresDeCampoRespetaStatusYCodigo() throws Exception {
        esperarError(mvc.perform(get("/prueba/negocio")), 409, "TURNO_OCUPADO", "/prueba/negocio")
                .andExpect(jsonPath("$.mensaje").value("El turno ya está reservado."))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    @Test
    void negocioConErrorDeCampoLoDevuelveEnErrores() throws Exception {
        esperarError(mvc.perform(get("/prueba/negocio-campo")), 400, "VALIDACION", "/prueba/negocio-campo")
                .andExpect(jsonPath("$.mensaje").value("El DNI no coincide con la reserva."))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value("cliente.dni"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El DNI no coincide con la reserva."));
    }

    // ---------- Validación de @Valid ----------

    @Test
    void cuerpoValidoNoProduceError() throws Exception {
        mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"nombre\":\"Rosa\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unSoloCampoInvalidoUsaSuMensajeComoMensajeGeneral() throws Exception {
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"123\",\"nombre\":\"Rosa\"}")), 400, "VALIDACION", "/prueba/formulario")
                .andExpect(jsonPath("$.mensaje").value("El DNI debe tener 8 dígitos."))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value("dni"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El DNI debe tener 8 dígitos."));
    }

    @Test
    void variosCamposInvalidosDanMensajeGeneralYUnErrorPorCampo() throws Exception {
        // dni vacío falla @NotBlank y @Dni a la vez: solo se informa un mensaje para ese campo.
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"\",\"nombre\":\"R\"}")), 400, "VALIDACION", "/prueba/formulario")
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos marcados."))
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[?(@.campo == 'dni')]", hasSize(1)))
                .andExpect(jsonPath("$.errores[?(@.campo == 'nombre')]", hasSize(1)));
    }

    @Test
    void errorGlobalDelObjetoUsaElNombreDelObjetoComoCampo() throws Exception {
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"nombre\":\"Rechazar\"}")), 400, "VALIDACION", "/prueba/formulario")
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value("formulario"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El formulario no es coherente."))
                .andExpect(jsonPath("$.mensaje").value("El formulario no es coherente."));
    }

    @Test
    void parametroAnotadoInvalidoUsaElNombreDelParametro() throws Exception {
        esperarError(mvc.perform(get("/prueba/dni").param("dni", "123")), 400, "VALIDACION", "/prueba/dni")
                .andExpect(jsonPath("$.errores[0].campo").value("dni"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El DNI debe tener 8 dígitos."));
    }

    @Test
    void cuerpoInvalidoJuntoAParametroAnotadoInformaElCampoReal() throws Exception {
        // Con validación de método (parámetro con @CodigoReserva), el @Valid del cuerpo se reporta por campo.
        esperarError(mvc.perform(post("/prueba/mixto").param("codigo", "ALC-7K3M9P")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"123\",\"nombre\":\"Rosa\"}")), 400, "VALIDACION", "/prueba/mixto")
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value("dni"));
    }

    @Test
    void constraintViolationTomaElUltimoTramoDeLaRuta() throws Exception {
        esperarError(mvc.perform(get("/prueba/restricciones")), 400, "VALIDACION", "/prueba/restricciones")
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos marcados."))
                .andExpect(jsonPath("$.errores[?(@.campo == 'dni')]", hasSize(1)))       // «cliente.dni» → «dni»
                .andExpect(jsonPath("$.errores[?(@.campo == 'codigo')]", hasSize(1)));   // ruta sin punto
    }

    // ---------- Cuerpo ilegible ----------

    @Test
    void jsonMalFormadoDaCuerpoInvalido() throws Exception {
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\": ")), 400, "CUERPO_INVALIDO", "/prueba/formulario")
                .andExpect(jsonPath("$.mensaje").value("La solicitud no tiene un formato JSON válido."))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    @Test
    void valorConFormatoIncorrectoSeAsociaAlCampo() throws Exception {
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"nombre\":\"Rosa\",\"edad\":\"abc\"}")), 400, "VALIDACION", "/prueba/formulario")
                .andExpect(jsonPath("$.errores[0].campo").value("edad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El valor «abc» no tiene el formato esperado."));
    }

    @Test
    void valorConFormatoIncorrectoDentroDeUnaListaIncluyeElIndice() throws Exception {
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"nombre\":\"Rosa\",\"items\":[{\"cantidad\":\"x\"}]}")),
                400, "VALIDACION", "/prueba/formulario")
                .andExpect(jsonPath("$.errores[0].campo").value("items.[0].cantidad"));
    }

    @Test
    void tipoDeJsonIncompatibleSeAsociaAlCampo() throws Exception {
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"nombre\":\"Rosa\",\"etiquetas\":{\"a\":1}}")), 400, "VALIDACION", "/prueba/formulario")
                .andExpect(jsonPath("$.errores[0].campo").value("etiquetas"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El valor de este campo no tiene el formato esperado."));
    }

    @Test
    void tipoIncompatibleDentroDeUnaListaIncluyeElIndice() throws Exception {
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"nombre\":\"Rosa\",\"items\":[{\"cantidad\":{\"a\":1}}]}")),
                400, "VALIDACION", "/prueba/formulario")
                .andExpect(jsonPath("$.errores[0].campo").value("items.[0].cantidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El valor de este campo no tiene el formato esperado."));
    }

    // ---------- Parámetros, partes y archivos ----------

    @Test
    void parametroFaltante() throws Exception {
        esperarError(mvc.perform(get("/prueba/pagina")), 400, "VALIDACION", "/prueba/pagina")
                .andExpect(jsonPath("$.errores[0].campo").value("pagina"))
                .andExpect(jsonPath("$.mensaje").value("Este dato es obligatorio."));
    }

    @Test
    void parametroDeTipoIncorrecto() throws Exception {
        esperarError(mvc.perform(get("/prueba/pagina").param("pagina", "dos")), 400, "VALIDACION", "/prueba/pagina")
                .andExpect(jsonPath("$.errores[0].campo").value("pagina"))
                .andExpect(jsonPath("$.mensaje").value("El valor «dos» no es válido."));
    }

    @Test
    void parteMultipartFaltante() throws Exception {
        esperarError(mvc.perform(multipart("/prueba/archivo")), 400, "VALIDACION", "/prueba/archivo")
                .andExpect(jsonPath("$.errores[0].campo").value("archivo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este dato es obligatorio."));
    }

    @Test
    void archivoDemasiadoGrande() throws Exception {
        esperarError(mvc.perform(get("/prueba/archivo-grande")), 400, "ARCHIVO_MUY_GRANDE", "/prueba/archivo-grande")
                .andExpect(jsonPath("$.errores[0].campo").value("archivo"))
                .andExpect(jsonPath("$.mensaje", containsString("5 MB")));
    }

    // ---------- HTTP ----------

    @Test
    void metodoNoPermitido() throws Exception {
        esperarError(mvc.perform(get("/prueba/formulario")), 405, "METODO_NO_PERMITIDO", "/prueba/formulario");
    }

    @Test
    void tipoDeContenidoNoSoportado() throws Exception {
        esperarError(mvc.perform(post("/prueba/formulario").contentType(MediaType.TEXT_PLAIN).content("hola")),
                415, "TIPO_NO_SOPORTADO", "/prueba/formulario");
    }

    @Test
    void recursoInexistente() throws Exception {
        esperarError(mvc.perform(get("/prueba/sin-recurso")), 404, "NO_ENCONTRADO", "/prueba/sin-recurso")
                .andExpect(jsonPath("$.mensaje").value("La ruta solicitada no existe."));
    }

    @Test
    void excepcionInesperadaDa500SinFiltrarDetalles() throws Exception {
        esperarError(mvc.perform(get("/prueba/inesperado")), 500, "ERROR_INTERNO", "/prueba/inesperado")
                .andExpect(jsonPath("$.mensaje", is("Ocurrió un error inesperado. Intenta de nuevo en unos segundos.")));
    }

    // ---------- Infraestructura (seguridad y datos) ----------

    @Test
    void accesoDenegadoDa403AunqueElGlobalTengaException() throws Exception {
        esperarError(mvc.perform(get("/prueba/denegado")), 403, "SIN_PERMISO", "/prueba/denegado")
                .andExpect(jsonPath("$.mensaje").value("Tu rol no tiene permiso para esta acción."));
        esperarError(mvc.perform(get("/prueba/autorizacion-denegada")), 403, "SIN_PERMISO", "/prueba/autorizacion-denegada");
    }

    @Test
    void autenticacionFallidaDa401() throws Exception {
        esperarError(mvc.perform(get("/prueba/no-autenticado")), 401, "NO_AUTENTICADO", "/prueba/no-autenticado")
                .andExpect(jsonPath("$.mensaje").value("Inicia sesión para continuar."));
    }

    @Test
    void violacionDeIntegridadDa409() throws Exception {
        esperarError(mvc.perform(get("/prueba/integridad")), 409, "CONFLICTO_DATOS", "/prueba/integridad")
                .andExpect(jsonPath("$.mensaje", containsString("chocan con un registro existente")));
    }

    @Test
    void ordenPorCampoInexistenteDa400ConCampoSort() throws Exception {
        esperarError(mvc.perform(get("/prueba/orden")), 400, "VALIDACION", "/prueba/orden")
                .andExpect(jsonPath("$.mensaje").value("No se puede ordenar por «campoX»."))
                .andExpect(jsonPath("$.errores[0].campo").value("sort"));
    }

    @Test
    void bloqueosOptimistaYPesimistaDan409DeConcurrencia() throws Exception {
        esperarError(mvc.perform(get("/prueba/optimista")), 409, "CONFLICTO_CONCURRENCIA", "/prueba/optimista");
        esperarError(mvc.perform(get("/prueba/pesimista")), 409, "CONFLICTO_CONCURRENCIA", "/prueba/pesimista")
                .andExpect(jsonPath("$.mensaje", containsString("al mismo tiempo")));
    }

    // ---------- Soporte ----------

    @ConsistenciaFormulario
    record Formulario(@NotBlank @Dni String dni, @NotBlank @Size(min = 2, max = 60) String nombre, Integer edad,
                      List<String> etiquetas, List<Item> items) {
    }

    record Item(Integer cantidad) {
    }

    record Envoltura(@Valid Formulario cliente, @CodigoReserva String codigo) {
    }

    /** Restricción de clase (error global, sin campo) para cubrir los «global errors» del BindingResult. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = ConsistenciaFormulario.Validador.class)
    @interface ConsistenciaFormulario {
        String message() default "El formulario no es coherente.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        class Validador implements ConstraintValidator<ConsistenciaFormulario, Formulario> {
            @Override
            public boolean isValid(Formulario f, ConstraintValidatorContext ctx) {
                return f == null || !"Rechazar".equals(f.nombre());
            }
        }
    }

    @RestController
    @RequestMapping("/prueba")
    static class ControladorPrueba {

        private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

        @GetMapping("/negocio")
        void negocio() {
            throw NegocioExcepcion.conflicto("TURNO_OCUPADO", "El turno ya está reservado.");
        }

        @GetMapping("/negocio-campo")
        void negocioCampo() {
            throw NegocioExcepcion.validacion("cliente.dni", "El DNI no coincide con la reserva.");
        }

        @PostMapping("/formulario")
        void formulario(@Valid @RequestBody Formulario f) {
        }

        @PostMapping("/mixto")
        void mixto(@Valid @RequestBody Formulario f, @RequestParam @CodigoReserva String codigo) {
        }

        @GetMapping("/dni")
        void dni(@RequestParam @Dni String dni) {
        }

        @GetMapping("/restricciones")
        void restricciones() {
            var envoltura = new Envoltura(new Formulario("123", "Rosa", null, null, null), "XYZ");
            throw new ConstraintViolationException(validador.validate(envoltura));
        }

        @GetMapping("/pagina")
        void pagina(@RequestParam int pagina) {
        }

        @PostMapping("/archivo")
        void archivo(@RequestPart("archivo") MultipartFile archivo) {
        }

        @GetMapping("/archivo-grande")
        void archivoGrande() {
            throw new MaxUploadSizeExceededException(5L * 1024 * 1024);
        }

        @GetMapping("/sin-recurso")
        void sinRecurso() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "prueba/sin-recurso");
        }

        @GetMapping("/inesperado")
        void inesperado() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }

        @GetMapping("/denegado")
        void denegado() {
            throw new AccessDeniedException("no");
        }

        @GetMapping("/autorizacion-denegada")
        void autorizacionDenegada() {
            throw new AuthorizationDeniedException("no");
        }

        @GetMapping("/no-autenticado")
        void noAutenticado() {
            throw new BadCredentialsException("credenciales malas");
        }

        @GetMapping("/integridad")
        void integridad() {
            throw new DataIntegrityViolationException("dup", new SQLException("Duplicate entry 'x'"));
        }

        @GetMapping("/orden")
        void orden() {
            throw new PropertyReferenceException("campoX", TypeInformation.of(Formulario.class), List.of());
        }

        @GetMapping("/optimista")
        void optimista() {
            throw new OptimisticLockingFailureException("versión cambiada");
        }

        @GetMapping("/pesimista")
        void pesimista() {
            throw new PessimisticLockingFailureException("bloqueado");
        }
    }
}

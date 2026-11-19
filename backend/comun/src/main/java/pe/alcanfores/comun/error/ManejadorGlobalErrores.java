package pe.alcanfores.comun.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Traduce cualquier excepción al cuerpo {@link ErrorApi} con mensajes en español (RNF-03). */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    @ExceptionHandler(NegocioExcepcion.class)
    public ResponseEntity<ErrorApi> negocio(NegocioExcepcion e, HttpServletRequest req) {
        return responder(e.getStatus(), e.getCodigo(), e.getMessage(), req, e.getErrores());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> cuerpoInvalido(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errores.add(new ErrorCampo(fe.getField(), fe.getDefaultMessage()));
        }
        e.getBindingResult().getGlobalErrors()
                .forEach(ge -> errores.add(new ErrorCampo(ge.getObjectName(), ge.getDefaultMessage())));
        return validacion(errores, req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorApi> parametrosInvalidos(HandlerMethodValidationException e, HttpServletRequest req) {
        List<ErrorCampo> errores = new ArrayList<>();
        // Si el parámetro es un cuerpo @Valid, cada error es un FieldError con el campo real (cliente.dni);
        // si es un @PathVariable/@RequestParam anotado, se usa el nombre del parámetro.
        e.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(err ->
                errores.add(new ErrorCampo(err instanceof FieldError fe ? fe.getField()
                        : r.getMethodParameter().getParameterName(), err.getDefaultMessage()))));
        return validacion(errores, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorApi> restricciones(ConstraintViolationException e, HttpServletRequest req) {
        List<ErrorCampo> errores = e.getConstraintViolations().stream()
                .map(v -> {
                    String ruta = v.getPropertyPath().toString();
                    String campo = ruta.contains(".") ? ruta.substring(ruta.lastIndexOf('.') + 1) : ruta;
                    return new ErrorCampo(campo, v.getMessage());
                })
                .collect(Collectors.toList());
        return validacion(errores, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorApi> ilegible(HttpMessageNotReadableException e, HttpServletRequest req) {
        Throwable causa = e.getCause();
        if (causa instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
            String campo = ife.getPath().stream().map(r -> r.getFieldName() == null ? "[" + r.getIndex() + "]" : r.getFieldName())
                    .collect(Collectors.joining("."));
            return validacion(List.of(new ErrorCampo(campo, "El valor «" + ife.getValue() + "» no tiene el formato esperado.")), req);
        }
        if (causa instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String campo = mie.getPath().stream().map(r -> r.getFieldName() == null ? "[" + r.getIndex() + "]" : r.getFieldName())
                    .collect(Collectors.joining("."));
            return validacion(List.of(new ErrorCampo(campo, "El valor de este campo no tiene el formato esperado.")), req);
        }
        return responder(400, "CUERPO_INVALIDO", "La solicitud no tiene un formato JSON válido.", req, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorApi> faltaParametro(MissingServletRequestParameterException e, HttpServletRequest req) {
        return validacion(List.of(new ErrorCampo(e.getParameterName(), "Este dato es obligatorio.")), req);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorApi> faltaParte(MissingServletRequestPartException e, HttpServletRequest req) {
        return validacion(List.of(new ErrorCampo(e.getRequestPartName(), "Este dato es obligatorio.")), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorApi> tipoIncorrecto(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return validacion(List.of(new ErrorCampo(e.getName(), "El valor «" + e.getValue() + "» no es válido.")), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorApi> archivoGrande(MaxUploadSizeExceededException e, HttpServletRequest req) {
        return responder(400, "ARCHIVO_MUY_GRANDE", "El archivo supera el tamaño máximo de 5 MB.", req,
                List.of(new ErrorCampo("archivo", "El archivo supera el tamaño máximo de 5 MB.")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorApi> metodo(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return responder(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido en esta ruta.", req, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorApi> tipoMedio(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return responder(415, "TIPO_NO_SOPORTADO", "Tipo de contenido no soportado.", req, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorApi> sinRuta(NoResourceFoundException e, HttpServletRequest req) {
        return responder(404, "NO_ENCONTRADO", "La ruta solicitada no existe.", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApi> inesperado(Exception e, HttpServletRequest req) {
        // Las excepciones de seguridad y de datos las atiende ManejadorErroresInfraestructura (más específico).
        log.error("Error no controlado en {} {}", req.getMethod(), req.getRequestURI(), e);
        return responder(500, "ERROR_INTERNO", "Ocurrió un error inesperado. Intenta de nuevo en unos segundos.", req, List.of());
    }

    static ResponseEntity<ErrorApi> responder(int status, String codigo, String mensaje, HttpServletRequest req,
                                              List<ErrorCampo> errores) {
        return ResponseEntity.status(HttpStatus.valueOf(status))
                .body(ErrorApi.de(status, codigo, mensaje, req.getRequestURI(), errores));
    }

    private ResponseEntity<ErrorApi> validacion(List<ErrorCampo> errores, HttpServletRequest req) {
        // Un campo vacío puede fallar @NotBlank y @Size a la vez: se informa solo el primer mensaje de cada campo.
        List<ErrorCampo> unicos = new ArrayList<>(errores.stream()
                .collect(Collectors.toMap(ErrorCampo::campo, ec -> ec, (a, b) -> a, java.util.LinkedHashMap::new))
                .values());
        String mensaje = unicos.size() == 1 ? unicos.get(0).mensaje() : "Revisa los datos marcados.";
        return responder(400, "VALIDACION", mensaje, req, unicos);
    }
}

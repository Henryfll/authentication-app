package com.henryfll.auth.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce todas las excepciones de la API a respuestas <a
 * href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457 — Problem Details</a>.
 *
 * <p>Todas las respuestas de error incluyen, además de los campos estándar ({@code type}, {@code
 * title}, {@code status}, {@code detail}, {@code instance}), las propiedades {@code code} ({@link
 * ErrorCode}) y {@code timestamp}. Los errores de validación añaden {@code errors}.
 *
 * <p>Los errores inesperados nunca exponen detalles internos al cliente: se registran en el log y
 * se devuelve un mensaje genérico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  static final String CODE = "code";
  static final String TIMESTAMP = "timestamp";
  static final String ERRORS = "errors";

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Errores de negocio lanzados por los módulos. */
  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> handleDomainException(
      DomainException ex, HttpServletRequest request) {
    ErrorCode code = ex.code();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(code.type().httpStatus()), ex.getMessage());
    problem.setTitle(code.title());
    ex.details().forEach(problem::setProperty);
    enrich(problem, code, request.getRequestURI());
    if (ex instanceof RateLimitedException limited) {
      return ResponseEntity.status(problem.getStatus())
          .header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.retryAfterSeconds()))
          .body(problem);
    }
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /**
   * Restricción de la base de datos violada (p. ej. una carrera entre dos altas con el mismo email)
   * o modificación concurrente detectada por el bloqueo optimista.
   */
  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> handleConflict(Exception ex, HttpServletRequest request) {
    log.warn(
        "Conflict processing {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        ex.toString());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "The request conflicts with the current state of the resource");
    problem.setTitle(ErrorCode.CONFLICT.title());
    enrich(problem, ErrorCode.CONFLICT, request.getRequestURI());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /** Cualquier error no previsto: 500 sin filtrar información interna. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error(
        "Unexpected error processing {} {}", request.getMethod(), request.getRequestURI(), ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    problem.setTitle(ErrorCode.INTERNAL_ERROR.title());
    enrich(problem, ErrorCode.INTERNAL_ERROR, request.getRequestURI());
    return ResponseEntity.internalServerError().body(problem);
  }

  /** Errores de Bean Validation sobre el body ({@code @Valid @RequestBody}). */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = validationProblem();
    List<Map<String, Object>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe ->
                Map.<String, Object>of(
                    "field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
            .toList();
    problem.setProperty(ERRORS, errors);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /** Errores de validación sobre parámetros del método (path, query, headers). */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = validationProblem();
    List<Map<String, Object>> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(result ->
                result.getResolvableErrors().stream()
                    .map(error ->
                        Map.<String, Object>of(
                            "field",
                            String.valueOf(result.getMethodParameter().getParameterName()),
                            "message",
                            String.valueOf(error.getDefaultMessage()))))
            .toList();
    problem.setProperty(ERRORS, errors);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /**
   * Punto común por el que pasan todas las excepciones estándar de Spring MVC (404, 405, 415, body
   * ilegible, etc.). Añade {@code code}, {@code timestamp} e {@code instance}.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ProblemDetail problem;
    if (body instanceof ProblemDetail pd) {
      problem = pd;
    } else if (ex instanceof ErrorResponse errorResponse) {
      // Reutiliza el detail que Spring genera para sus propias excepciones.
      problem = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
    } else {
      problem = ProblemDetail.forStatus(statusCode);
    }
    enrich(problem, codeFor(statusCode), requestUri(request));
    return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
  }

  private static ProblemDetail validationProblem() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    problem.setTitle(ErrorCode.VALIDATION_ERROR.title());
    problem.setProperty(CODE, ErrorCode.VALIDATION_ERROR.name());
    return problem;
  }

  /**
   * Añade {@code code} (si aún no lo tiene), {@code timestamp} e {@code instance}. Nota: {@link
   * ProblemDetail#getProperties()} devuelve {@code null} mientras no se haya añadido ninguna.
   */
  private static void enrich(ProblemDetail problem, ErrorCode code, String requestUri) {
    Map<String, Object> properties = problem.getProperties();
    if (properties == null || !properties.containsKey(CODE)) {
      problem.setProperty(CODE, code.name());
    }
    problem.setProperty(TIMESTAMP, Instant.now());
    if (problem.getInstance() == null && requestUri != null) {
      problem.setInstance(URI.create(requestUri));
    }
  }

  static ErrorCode codeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> ErrorCode.MALFORMED_REQUEST;
      case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
      case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
      case 409 -> ErrorCode.CONFLICT;
      case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
      default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.MALFORMED_REQUEST;
    };
  }

  private static String requestUri(WebRequest request) {
    if (request instanceof NativeWebRequest nativeRequest
        && nativeRequest.getNativeRequest() instanceof HttpServletRequest servletRequest) {
      return servletRequest.getRequestURI();
    }
    return null;
  }
}

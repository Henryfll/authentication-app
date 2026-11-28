package com.henryfll.auth.shared.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Cuerpo de todas las respuestas de error (RFC 9457, {@code application/problem+json}).
 *
 * <p><strong>Solo documenta el contrato OpenAPI</strong>: los errores reales los construye {@code
 * GlobalExceptionHandler} con {@code ProblemDetail}. Si cambia el formato de los errores, hay que
 * actualizar este registro (lo comprueba {@code OpenApiContractTest}).
 */
@Schema(name = "Problem", description = "Error de la API (RFC 9457 Problem Details)")
public record ProblemResponse(
    @Schema(
            requiredMode = REQUIRED,
            description =
                "Identificador estable del error. Es lo que debe usar el cliente para decidir")
        ErrorCode code,
    @Schema(requiredMode = REQUIRED, description = "Estado HTTP") int status,
    @Schema(requiredMode = REQUIRED, description = "Título breve, en inglés. No mostrar al usuario")
        String title,
    @Schema(description = "Explicación, en inglés. No mostrar al usuario") String detail,
    @Schema(description = "Ruta de la petición que falló") String instance,
    @Schema(requiredMode = REQUIRED, description = "Momento del error, en UTC") Instant timestamp,
    @Schema(description = "Siempre `about:blank` cuando está presente") String type,
    @Schema(description = "Errores por campo. Solo en errores de validación")
        List<FieldError> errors,
    @Schema(description = "Segundos que hay que esperar. Solo en `TOO_MANY_REQUESTS`")
        Integer retryAfterSeconds,
    @Schema(
            description =
                "Identificador que se buscó y no existe. Solo en los errores 404 de negocio. Es un"
                    + " texto: en `ACCESS_NOT_FOUND` es `idUsuario@idAplicación`")
        String id) {

  /** Error de validación de un campo concreto. */
  @Schema(name = "ProblemFieldError")
  public record FieldError(
      @Schema(requiredMode = REQUIRED, description = "Nombre del campo del cuerpo o del parámetro")
          String field,
      @Schema(requiredMode = REQUIRED, description = "Motivo, en inglés") String message) {}
}

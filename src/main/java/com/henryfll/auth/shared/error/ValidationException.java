package com.henryfll.auth.shared.error;

import java.util.List;
import java.util.Map;

/**
 * Un valor de dominio no es válido (p. ej. un email mal formado). Se expone igual que los errores
 * de Bean Validation: con la lista {@code errors} de campo y mensaje.
 */
public class ValidationException extends DomainException {

  public ValidationException(String field, String message) {
    this(ErrorCode.VALIDATION_ERROR, field, message);
  }

  public ValidationException(ErrorCode code, String field, String message) {
    super(code, message, Map.of("errors", List.of(Map.of("field", field, "message", message))));
  }
}

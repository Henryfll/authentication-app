package com.henryfll.auth.shared.error;

import java.util.Map;
import java.util.Objects;

/**
 * Excepción base para errores de negocio. Las capas de dominio y aplicación lanzan subclases de
 * esta excepción, y {@link GlobalExceptionHandler} las convierte en Problem Details.
 *
 * <p>{@code details} se expone al cliente: nunca debe contener datos sensibles.
 */
public class DomainException extends RuntimeException {

  private final ErrorCode code;
  private final transient Map<String, Object> details;

  public DomainException(ErrorCode code, String message) {
    this(code, message, Map.of());
  }

  public DomainException(ErrorCode code, String message, Map<String, Object> details) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
    this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
  }

  public ErrorCode code() {
    return code;
  }

  public Map<String, Object> details() {
    return details;
  }
}

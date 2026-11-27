package com.henryfll.auth.shared.error;

/**
 * Códigos de error de la API. Se devuelven en el campo {@code code} de cada respuesta Problem
 * Details (RFC 9457) para que los clientes puedan reaccionar sin depender del texto del mensaje.
 *
 * <p>Cada módulo añadirá aquí sus propios códigos (p. ej. {@code AUTH_INVALID_CREDENTIALS}).
 */
public enum ErrorCode {
  VALIDATION_ERROR(ErrorType.VALIDATION, "Validation error"),
  MALFORMED_REQUEST(ErrorType.VALIDATION, "Malformed request"),
  RESOURCE_NOT_FOUND(ErrorType.NOT_FOUND, "Resource not found"),
  METHOD_NOT_ALLOWED(ErrorType.METHOD_NOT_ALLOWED, "Method not allowed"),
  UNSUPPORTED_MEDIA_TYPE(ErrorType.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
  CONFLICT(ErrorType.CONFLICT, "Conflict"),
  INTERNAL_ERROR(ErrorType.INTERNAL, "Internal server error");

  private final ErrorType type;
  private final String title;

  ErrorCode(ErrorType type, String title) {
    this.type = type;
    this.title = title;
  }

  public ErrorType type() {
    return type;
  }

  public String title() {
    return title;
  }
}

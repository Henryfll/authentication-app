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
  BUSINESS_RULE_VIOLATION(ErrorType.UNPROCESSABLE, "Business rule violation"),
  INTERNAL_ERROR(ErrorType.INTERNAL, "Internal server error"),

  // Módulo user
  USER_NOT_FOUND(ErrorType.NOT_FOUND, "User not found"),
  USER_EMAIL_ALREADY_EXISTS(ErrorType.CONFLICT, "Email already registered"),
  PASSWORD_POLICY_VIOLATION(ErrorType.VALIDATION, "Password does not meet the policy"),

  // Módulo apps
  APPLICATION_NOT_FOUND(ErrorType.NOT_FOUND, "Application not found"),
  APPLICATION_CODE_ALREADY_EXISTS(ErrorType.CONFLICT, "Application code already exists"),
  CLIENT_NOT_FOUND(ErrorType.NOT_FOUND, "Client not found"),
  CLIENT_ID_ALREADY_EXISTS(ErrorType.CONFLICT, "Client id already exists"),
  INVALID_CLIENT_CONFIGURATION(ErrorType.UNPROCESSABLE, "Invalid client configuration"),
  PERMISSION_NOT_FOUND(ErrorType.NOT_FOUND, "Permission not found"),
  PERMISSION_ALREADY_EXISTS(ErrorType.CONFLICT, "Permission already exists"),
  ROLE_NOT_FOUND(ErrorType.NOT_FOUND, "Role not found"),
  ROLE_ALREADY_EXISTS(ErrorType.CONFLICT, "Role already exists");

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

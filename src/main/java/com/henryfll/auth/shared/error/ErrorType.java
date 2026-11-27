package com.henryfll.auth.shared.error;

/**
 * Categoría de un error, independiente del protocolo. La capa web la traduce a un código HTTP, de
 * modo que el dominio no depende de Spring.
 */
public enum ErrorType {
  VALIDATION(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  METHOD_NOT_ALLOWED(405),
  CONFLICT(409),
  UNSUPPORTED_MEDIA_TYPE(415),
  TOO_MANY_REQUESTS(429),
  INTERNAL(500);

  private final int httpStatus;

  ErrorType(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  public int httpStatus() {
    return httpStatus;
  }
}

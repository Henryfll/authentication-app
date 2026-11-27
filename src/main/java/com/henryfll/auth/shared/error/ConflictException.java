package com.henryfll.auth.shared.error;

/** El recurso ya existe o choca con el estado actual (HTTP 409). */
public class ConflictException extends DomainException {

  public ConflictException(ErrorCode code, String message) {
    super(code, message);
  }
}

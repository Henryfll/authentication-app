package com.henryfll.auth.shared.error;

import java.util.Map;

/** Recurso inexistente. Incluye el identificador buscado en los detalles de la respuesta. */
public class NotFoundException extends DomainException {

  public NotFoundException(ErrorCode code, Object id) {
    super(code, code.title() + ": " + id, Map.of("id", String.valueOf(id)));
  }
}

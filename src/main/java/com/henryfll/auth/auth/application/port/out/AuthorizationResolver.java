package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.Authorization;
import java.util.Optional;
import java.util.UUID;

public interface AuthorizationResolver {

  /** Vacío si el usuario no tiene un acceso efectivo a la aplicación. */
  Optional<Authorization> resolve(UUID userId, UUID applicationId);
}

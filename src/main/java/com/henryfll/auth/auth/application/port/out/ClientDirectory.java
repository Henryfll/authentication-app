package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.LoginClient;
import java.util.Optional;
import java.util.UUID;

public interface ClientDirectory {

  Optional<LoginClient> findByClientId(String clientId);

  /** Busca por el identificador interno del cliente (el que se guarda en la sesión). */
  Optional<LoginClient> findById(UUID id);
}

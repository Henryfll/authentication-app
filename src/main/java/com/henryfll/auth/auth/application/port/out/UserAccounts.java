package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;

/** Estado actual de un usuario (para el refresh, que no vuelve a pedir la contraseña). */
public interface UserAccounts {

  Optional<AuthenticatedUser> findById(UUID userId);
}

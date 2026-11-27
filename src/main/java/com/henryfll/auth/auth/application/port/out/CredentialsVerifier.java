package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.AuthenticatedUser;
import java.util.Optional;

public interface CredentialsVerifier {

  /** Vacío si el email no existe o la contraseña no coincide. */
  Optional<AuthenticatedUser> verify(String email, String password);
}

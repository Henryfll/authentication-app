package com.henryfll.auth.user.application.port.in;

import com.henryfll.auth.user.domain.User;
import java.util.Optional;

/** Comprobación de email y contraseña para el login, con bloqueo progresivo por fallos. */
public interface VerifyCredentialsUseCase {

  /**
   * Verifica las credenciales y actualiza el contador de fallos.
   *
   * <p>El tiempo de respuesta es similar en todos los casos: si el email no existe o la cuenta está
   * bloqueada temporalmente se calcula igualmente un hash. Mientras dura el bloqueo no se comprueba
   * la contraseña, para que no sirva como oráculo en un ataque de fuerza bruta.
   */
  CredentialsCheck verify(String email, String password);

  enum Outcome {
    VALID,
    UNKNOWN_USER,
    INVALID_PASSWORD,
    TEMPORARILY_LOCKED
  }

  /**
   * @param user presente salvo con {@code UNKNOWN_USER}; con {@code VALID} puede estar en un estado
   *     no activo (el llamante decide).
   */
  record CredentialsCheck(Outcome outcome, Optional<User> user) {

    public boolean valid() {
      return outcome == Outcome.VALID;
    }
  }
}

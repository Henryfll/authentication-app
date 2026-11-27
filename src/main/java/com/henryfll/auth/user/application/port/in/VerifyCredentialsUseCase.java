package com.henryfll.auth.user.application.port.in;

import com.henryfll.auth.user.domain.User;
import java.util.Optional;

/** Comprobación de email y contraseña para el login. */
public interface VerifyCredentialsUseCase {

  /**
   * Devuelve el usuario si el email existe y la contraseña coincide, con independencia de su estado
   * (el llamante decide qué hacer con un usuario bloqueado).
   *
   * <p>Si el email no existe se calcula igualmente un hash, para que el tiempo de respuesta no
   * revele qué emails están registrados.
   */
  Optional<User> verify(String email, String password);
}

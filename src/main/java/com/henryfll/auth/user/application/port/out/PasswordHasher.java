package com.henryfll.auth.user.application.port.out;

/** Hash de contraseñas (Argon2id). */
public interface PasswordHasher {

  String hash(String rawPassword);

  boolean matches(String rawPassword, String hash);
}

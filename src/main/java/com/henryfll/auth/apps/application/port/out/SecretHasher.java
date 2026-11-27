package com.henryfll.auth.apps.application.port.out;

/** Hash de los secretos de clientes confidenciales (Argon2id). */
public interface SecretHasher {

  String hash(String rawSecret);
}

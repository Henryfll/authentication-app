package com.henryfll.auth.apps.application.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generación de secretos y sufijos aleatorios criptográficamente seguros. */
final class ClientSecrets {

  private static final SecureRandom RANDOM = new SecureRandom();

  private ClientSecrets() {}

  /** Secreto de 256 bits en Base64 URL-safe (43 caracteres). */
  static String newSecret() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Sufijo hexadecimal corto para generar {@code client_id} únicos. */
  static String newSuffix() {
    byte[] bytes = new byte[3];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}

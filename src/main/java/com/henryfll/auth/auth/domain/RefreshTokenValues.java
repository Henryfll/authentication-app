package com.henryfll.auth.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generación y hash de los valores opacos de refresh token. */
public final class RefreshTokenValues {

  private static final SecureRandom RANDOM = new SecureRandom();

  private RefreshTokenValues() {}

  /** Valor aleatorio de 256 bits en Base64 URL-safe (43 caracteres). */
  public static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * SHA-256 en hexadecimal. Un hash rápido sin sal es suficiente porque el valor ya tiene 256 bits
   * de entropía: no se puede atacar por diccionario.
   */
  public static String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}

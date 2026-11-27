package com.henryfll.auth.user.domain;

import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.ValidationException;
import java.util.Locale;
import java.util.Set;

/**
 * Política de contraseñas basada en NIST SP 800-63B: longitud mínima, sin reglas de composición
 * arbitrarias y rechazo de contraseñas comunes o derivadas del email.
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 128;

  /** Contraseñas comunes de 12 o más caracteres (extracto de listas públicas de filtraciones). */
  private static final Set<String> COMMON_PASSWORDS =
      Set.of(
          "123456789012",
          "1234567890123",
          "12345678901234",
          "111111111111",
          "000000000000",
          "qwertyuiopas",
          "qwerty123456",
          "1q2w3e4r5t6y",
          "password1234",
          "password12345",
          "password123456",
          "passwordpassword",
          "contraseña123",
          "contrasena123",
          "iloveyou1234",
          "administrator",
          "admin1234567",
          "welcome12345",
          "letmein12345",
          "abcdefghijkl",
          "abc123456789",
          "changeme1234");

  private PasswordPolicy() {}

  /**
   * @throws ValidationException con código {@link ErrorCode#PASSWORD_POLICY_VIOLATION} si la
   *     contraseña no cumple la política.
   */
  public static void validate(String password, Email email) {
    if (password == null || password.length() < MIN_LENGTH) {
      throw violation("must be at least " + MIN_LENGTH + " characters long");
    }
    if (password.length() > MAX_LENGTH) {
      throw violation("must be at most " + MAX_LENGTH + " characters long");
    }
    if (password.isBlank()) {
      throw violation("must not be blank");
    }
    String normalized = password.toLowerCase(Locale.ROOT);
    if (COMMON_PASSWORDS.contains(normalized) || isSingleRepeatedCharacter(password)) {
      throw violation("is too common");
    }
    if (email != null && normalized.contains(email.localPart())) {
      throw violation("must not contain the email address");
    }
  }

  private static boolean isSingleRepeatedCharacter(String password) {
    return password.chars().distinct().count() == 1;
  }

  private static ValidationException violation(String message) {
    return new ValidationException(ErrorCode.PASSWORD_POLICY_VIOLATION, "password", message);
  }
}

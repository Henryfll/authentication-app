package com.henryfll.auth.apps.domain;

import java.util.regex.Pattern;

/**
 * Identificador estable y legible de una aplicación (p. ej. {@code erp}). Se usa como {@code aud}
 * del JWT, así que no puede cambiar una vez creada la aplicación.
 */
public record ApplicationCode(String value) {

  private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9-]{1,48}[a-z0-9]$");

  public ApplicationCode {
    value =
        DomainText.matching(
            "code",
            value == null ? null : value.toLowerCase(java.util.Locale.ROOT),
            FORMAT,
            "must be 3-50 characters: lowercase letters, digits and hyphens, starting with a letter");
  }

  @Override
  public String toString() {
    return value;
  }
}

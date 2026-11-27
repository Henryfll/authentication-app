package com.henryfll.auth.apps.domain;

import com.henryfll.auth.shared.error.ValidationException;
import java.util.regex.Pattern;

/** Validaciones de texto compartidas por las entidades del módulo. */
final class DomainText {

  private DomainText() {}

  static String required(String field, String value, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field, "must not be blank");
    }
    return maxLength(field, value.strip(), maxLength);
  }

  static String optional(String field, String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return maxLength(field, value.strip(), maxLength);
  }

  static String matching(String field, String value, Pattern pattern, String description) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field, "must not be blank");
    }
    String trimmed = value.strip();
    if (!pattern.matcher(trimmed).matches()) {
      throw new ValidationException(field, description);
    }
    return trimmed;
  }

  private static String maxLength(String field, String value, int maxLength) {
    if (value.length() > maxLength) {
      throw new ValidationException(field, "must be at most " + maxLength + " characters long");
    }
    return value;
  }
}

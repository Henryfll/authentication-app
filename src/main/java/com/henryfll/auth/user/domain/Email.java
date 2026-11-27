package com.henryfll.auth.user.domain;

import com.henryfll.auth.shared.error.ValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Email normalizado (sin espacios y en minúsculas). La base de datos lo compara como CITEXT. */
public record Email(String value) {

  public static final int MAX_LENGTH = 254;
  private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

  public Email {
    if (value == null || value.isBlank()) {
      throw new ValidationException("email", "must not be blank");
    }
    value = value.strip().toLowerCase(Locale.ROOT);
    if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
      throw new ValidationException("email", "must be a well-formed email address");
    }
  }

  public String localPart() {
    return value.substring(0, value.indexOf('@'));
  }

  @Override
  public String toString() {
    return value;
  }
}

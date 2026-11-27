package com.henryfll.auth.shared.error;

import java.time.Duration;
import java.util.Map;

/** Límite de intentos superado (HTTP 429). La respuesta incluye la cabecera {@code Retry-After}. */
public class RateLimitedException extends DomainException {

  private final Duration retryAfter;

  public RateLimitedException(Duration retryAfter) {
    super(
        ErrorCode.TOO_MANY_REQUESTS,
        "Too many attempts. Try again later",
        Map.of("retryAfterSeconds", seconds(retryAfter)));
    this.retryAfter = retryAfter;
  }

  public long retryAfterSeconds() {
    return seconds(retryAfter);
  }

  private static long seconds(Duration duration) {
    return Math.max(1, (long) Math.ceil(duration.toMillis() / 1000.0));
  }
}

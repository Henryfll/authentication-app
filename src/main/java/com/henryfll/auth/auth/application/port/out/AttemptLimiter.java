package com.henryfll.auth.auth.application.port.out;

import java.time.Duration;
import java.util.Optional;

/** Límite de intentos por clave (token bucket). */
public interface AttemptLimiter {

  enum Limit {
    LOGIN_BY_IP,
    LOGIN_BY_EMAIL,
    REFRESH_BY_IP
  }

  /**
   * Consume un intento.
   *
   * @return vacío si se permite; si no, el tiempo que hay que esperar.
   */
  Optional<Duration> tryAcquire(Limit limit, String key);
}

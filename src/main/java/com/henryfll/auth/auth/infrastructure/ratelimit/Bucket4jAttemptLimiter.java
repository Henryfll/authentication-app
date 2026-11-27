package com.henryfll.auth.auth.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.henryfll.auth.auth.application.port.out.AttemptLimiter;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Token bucket en memoria por clave. Los buckets se guardan en una caché acotada (tamaño y
 * caducidad por inactividad) para que un atacante no pueda agotar la memoria con claves distintas.
 *
 * <p>Los contadores son por instancia: con varias réplicas, cada una aplica el límite por separado.
 * Un backend compartido (Redis) queda para cuando se despliegue en clúster.
 */
@Component
class Bucket4jAttemptLimiter implements AttemptLimiter {

  private static final int MAX_TRACKED_KEYS = 100_000;

  private final RateLimitProperties properties;
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder()
          .maximumSize(MAX_TRACKED_KEYS)
          .expireAfterAccess(Duration.ofHours(1))
          .build();

  Bucket4jAttemptLimiter(RateLimitProperties properties) {
    this.properties = properties;
  }

  @Override
  public Optional<Duration> tryAcquire(Limit limit, String key) {
    RateLimitProperties.Policy policy = properties.policy(limit);
    if (policy.capacity() <= 0 || key == null || key.isBlank()) {
      return Optional.empty();
    }
    String bucketKey = limit.name() + ":" + key.strip().toLowerCase(Locale.ROOT);
    Bucket bucket = buckets.get(bucketKey, k -> newBucket(policy));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    return probe.isConsumed()
        ? Optional.empty()
        : Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
  }

  private static Bucket newBucket(RateLimitProperties.Policy policy) {
    return Bucket.builder()
        .addLimit(limit ->
            limit.capacity(policy.capacity()).refillGreedy(policy.capacity(), policy.period()))
        .build();
  }
}

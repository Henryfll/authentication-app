package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

  RefreshToken save(RefreshToken token);

  Optional<RefreshToken> findByHash(String tokenHash);

  /**
   * Marca el token como usado solo si todavía no lo estaba (operación atómica en base de datos).
   *
   * @return {@code false} si otro uso se adelantó: el token se está reutilizando.
   */
  boolean markUsed(UUID tokenId, Instant usedAt);

  void markReplaced(UUID tokenId, UUID replacedBy);
}

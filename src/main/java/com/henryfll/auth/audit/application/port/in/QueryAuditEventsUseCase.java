package com.henryfll.auth.audit.application.port.in;

import com.henryfll.auth.audit.domain.AuditEntry;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.time.Instant;
import java.util.UUID;

public interface QueryAuditEventsUseCase {

  /** Eventos que cumplen todos los filtros indicados, del más reciente al más antiguo. */
  PageResult<AuditEntry> search(AuditQuery query, PageQuery page);

  /**
   * Todos los filtros son opcionales.
   *
   * @param userId eventos en los que el usuario es actor o destinatario.
   * @param from inicio (inclusivo).
   * @param to fin (exclusivo).
   */
  record AuditQuery(
      AuditEventType type, UUID userId, UUID applicationId, Instant from, Instant to) {}
}

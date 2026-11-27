package com.henryfll.auth.audit.application.service;

import com.henryfll.auth.audit.application.port.in.QueryAuditEventsUseCase;
import com.henryfll.auth.audit.application.port.in.RecordAuditEventUseCase;
import com.henryfll.auth.audit.application.port.out.AuditEntryRepository;
import com.henryfll.auth.audit.domain.AuditEntry;
import com.henryfll.auth.shared.audit.AuditEvent;
import com.henryfll.auth.shared.error.ValidationException;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService implements RecordAuditEventUseCase, QueryAuditEventsUseCase {

  private final AuditEntryRepository entries;
  private final Clock clock;

  AuditService(AuditEntryRepository entries, Clock clock) {
    this.entries = entries;
    this.clock = clock;
  }

  /** Transacción propia: el evento persiste aunque la operación que lo registra falle. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditEvent event, UUID actorUserId, String ipAddress) {
    entries.save(
        new AuditEntry(
            UUID.randomUUID(),
            event.type(),
            actorUserId,
            event.targetUserId(),
            event.applicationId(),
            ipAddress,
            event.details(),
            clock.instant()));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<AuditEntry> search(AuditQuery query, PageQuery page) {
    if (query.from() != null && query.to() != null && !query.to().isAfter(query.from())) {
      throw new ValidationException("to", "must be after from");
    }
    return entries.search(query, page);
  }
}

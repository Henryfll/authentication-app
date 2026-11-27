package com.henryfll.auth.audit.domain;

import com.henryfll.auth.shared.audit.AuditEventType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Entrada del registro de auditoría. Inmutable: el registro solo admite altas. */
public record AuditEntry(
    UUID id,
    AuditEventType type,
    UUID actorUserId,
    UUID targetUserId,
    UUID applicationId,
    String ipAddress,
    Map<String, Object> details,
    Instant createdAt) {

  public AuditEntry {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(createdAt, "createdAt");
    details = details == null ? Map.of() : Map.copyOf(details);
  }
}

package com.henryfll.auth.shared.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de auditoría. El actor (usuario autenticado) y la IP los añade la implementación de {@link
 * AuditLog} a partir de la petición en curso.
 *
 * @param details datos adicionales. Nunca deben incluir contraseñas, tokens ni secretos.
 */
public record AuditEvent(
    AuditEventType type, UUID targetUserId, UUID applicationId, Map<String, Object> details) {

  public AuditEvent {
    Objects.requireNonNull(type, "type");
    details = details == null ? Map.of() : new LinkedHashMap<>(details);
  }

  public static AuditEvent of(AuditEventType type, UUID targetUserId, UUID applicationId) {
    return new AuditEvent(type, targetUserId, applicationId, Map.of());
  }

  public AuditEvent with(String key, Object value) {
    Map<String, Object> copy = new LinkedHashMap<>(details);
    if (value != null) {
      copy.put(key, value instanceof Enum<?> e ? e.name() : value);
    }
    return new AuditEvent(type, targetUserId, applicationId, copy);
  }
}

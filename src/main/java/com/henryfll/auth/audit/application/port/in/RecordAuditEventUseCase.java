package com.henryfll.auth.audit.application.port.in;

import com.henryfll.auth.shared.audit.AuditEvent;
import java.util.UUID;

public interface RecordAuditEventUseCase {

  /**
   * @param actorUserId usuario que realiza la acción; {@code null} si no hay usuario autenticado.
   * @param ipAddress IP de la petición; {@code null} si el evento no viene de una petición HTTP.
   */
  void record(AuditEvent event, UUID actorUserId, String ipAddress);
}

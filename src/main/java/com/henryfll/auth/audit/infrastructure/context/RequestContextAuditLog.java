package com.henryfll.auth.audit.infrastructure.context;

import com.henryfll.auth.audit.application.port.in.RecordAuditEventUseCase;
import com.henryfll.auth.shared.audit.AuditEvent;
import com.henryfll.auth.shared.audit.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Implementación de {@link AuditLog}: completa cada evento con el usuario autenticado (el {@code
 * sub} del access token) y la IP de la petición en curso.
 *
 * <p>Un fallo al auditar nunca rompe la operación auditada: se registra en el log y se continúa.
 */
@Component
class RequestContextAuditLog implements AuditLog {

  private static final Logger log = LoggerFactory.getLogger(RequestContextAuditLog.class);

  private final RecordAuditEventUseCase recorder;

  RequestContextAuditLog(RecordAuditEventUseCase recorder) {
    this.recorder = recorder;
  }

  @Override
  public void record(AuditEvent event) {
    try {
      recorder.record(event, currentUserId(), currentIp());
    } catch (RuntimeException e) {
      log.error("Could not record audit event {}", event.type(), e);
    }
  }

  private static UUID currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwt) {
      try {
        return UUID.fromString(jwt.getToken().getSubject());
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
  }

  private static String currentIp() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      HttpServletRequest request = attrs.getRequest();
      return request.getRemoteAddr();
    }
    return null;
  }
}

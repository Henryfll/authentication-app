package com.henryfll.auth.auth.infrastructure.integration;

import com.henryfll.auth.access.domain.AccessWithdrawnEvent;
import com.henryfll.auth.auth.application.port.in.RevokeSessionsUseCase;
import com.henryfll.auth.auth.domain.SessionRevocationReason;
import com.henryfll.auth.user.domain.PasswordChangedEvent;
import com.henryfll.auth.user.domain.UserDeactivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cierra sesiones cuando otros módulos retiran el acceso o desactivan una cuenta
 * (docs/ARCHITECTURE.md §5.4, regla 2). Se ejecuta en la misma transacción que el cambio que lo
 * provoca. Los access tokens ya emitidos siguen siendo válidos hasta que caducan.
 */
@Component
class SessionRevocationListener {

  private static final Logger log = LoggerFactory.getLogger(SessionRevocationListener.class);

  private final RevokeSessionsUseCase sessions;

  SessionRevocationListener(RevokeSessionsUseCase sessions) {
    this.sessions = sessions;
  }

  @EventListener
  void onAccessWithdrawn(AccessWithdrawnEvent event) {
    int revoked =
        sessions.revokeForApplication(
            event.userId(), event.applicationId(), SessionRevocationReason.ACCESS_REVOKED);
    log.info(
        "Access of user {} to application {} is {}: {} session(s) revoked",
        event.userId(),
        event.applicationId(),
        event.status(),
        revoked);
  }

  @EventListener
  void onPasswordChanged(PasswordChangedEvent event) {
    int revoked =
        event.currentSessionId() == null
            ? sessions.revokeAllForUser(event.userId(), SessionRevocationReason.PASSWORD_CHANGED)
            : sessions.revokeAllForUserExcept(
                event.userId(), event.currentSessionId(), SessionRevocationReason.PASSWORD_CHANGED);
    log.info("Password of user {} changed: {} session(s) revoked", event.userId(), revoked);
  }

  @EventListener
  void onUserDeactivated(UserDeactivatedEvent event) {
    int revoked = sessions.revokeAllForUser(event.userId(), SessionRevocationReason.USER_INACTIVE);
    log.info("User {} is {}: {} session(s) revoked", event.userId(), event.status(), revoked);
  }
}

package com.henryfll.auth.auth.application.port.in;

import com.henryfll.auth.auth.domain.SessionRevocationReason;
import java.util.UUID;

/** Revocación de sesiones desencadenada por otros módulos (acceso retirado, cuenta bloqueada). */
public interface RevokeSessionsUseCase {

  int revokeForApplication(UUID userId, UUID applicationId, SessionRevocationReason reason);

  int revokeAllForUser(UUID userId, SessionRevocationReason reason);
}

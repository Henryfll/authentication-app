package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.Session;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

  Session save(Session session);

  Optional<Session> findById(UUID id);

  List<Session> findActiveByUser(UUID userId);

  List<Session> findActiveByUserAndApplication(UUID userId, UUID applicationId);
}

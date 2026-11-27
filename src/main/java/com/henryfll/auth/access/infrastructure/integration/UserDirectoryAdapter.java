package com.henryfll.auth.access.infrastructure.integration;

import com.henryfll.auth.access.application.port.out.UserDirectory;
import com.henryfll.auth.shared.error.NotFoundException;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.domain.UserId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Traduce el puerto {@link UserDirectory} al caso de uso público del módulo user. */
@Component
class UserDirectoryAdapter implements UserDirectory {

  private final FindUsersUseCase users;

  UserDirectoryAdapter(FindUsersUseCase users) {
    this.users = users;
  }

  @Override
  public boolean exists(UUID userId) {
    try {
      users.get(new UserId(userId));
      return true;
    } catch (NotFoundException e) {
      return false;
    }
  }
}

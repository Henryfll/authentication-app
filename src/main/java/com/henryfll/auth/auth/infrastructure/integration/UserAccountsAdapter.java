package com.henryfll.auth.auth.infrastructure.integration;

import com.henryfll.auth.auth.application.port.out.UserAccounts;
import com.henryfll.auth.auth.domain.AuthenticatedUser;
import com.henryfll.auth.shared.error.NotFoundException;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class UserAccountsAdapter implements UserAccounts {

  private final FindUsersUseCase users;

  UserAccountsAdapter(FindUsersUseCase users) {
    this.users = users;
  }

  @Override
  public Optional<AuthenticatedUser> findById(UUID userId) {
    try {
      User user = users.get(new UserId(userId));
      return Optional.of(
          new AuthenticatedUser(
              user.id().value(), user.email().value(), user.status() == UserStatus.ACTIVE));
    } catch (NotFoundException e) {
      return Optional.empty();
    }
  }
}

package com.henryfll.auth.auth.infrastructure.integration;

import com.henryfll.auth.auth.application.port.out.CredentialsVerifier;
import com.henryfll.auth.auth.domain.AuthenticatedUser;
import com.henryfll.auth.user.application.port.in.VerifyCredentialsUseCase;
import com.henryfll.auth.user.domain.UserStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class CredentialsVerifierAdapter implements CredentialsVerifier {

  private final VerifyCredentialsUseCase verifyCredentials;

  CredentialsVerifierAdapter(VerifyCredentialsUseCase verifyCredentials) {
    this.verifyCredentials = verifyCredentials;
  }

  @Override
  public Optional<AuthenticatedUser> verify(String email, String password) {
    return verifyCredentials
        .verify(email, password)
        .map(user ->
            new AuthenticatedUser(
                user.id().value(), user.email().value(), user.status() == UserStatus.ACTIVE));
  }
}

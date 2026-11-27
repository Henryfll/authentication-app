package com.henryfll.auth.auth.infrastructure.integration;

import com.henryfll.auth.auth.application.port.out.CredentialsVerifier;
import com.henryfll.auth.auth.domain.AuthenticatedUser;
import com.henryfll.auth.auth.domain.CredentialsVerification;
import com.henryfll.auth.user.application.port.in.VerifyCredentialsUseCase;
import com.henryfll.auth.user.application.port.in.VerifyCredentialsUseCase.CredentialsCheck;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserStatus;
import org.springframework.stereotype.Component;

@Component
class CredentialsVerifierAdapter implements CredentialsVerifier {

  private final VerifyCredentialsUseCase verifyCredentials;

  CredentialsVerifierAdapter(VerifyCredentialsUseCase verifyCredentials) {
    this.verifyCredentials = verifyCredentials;
  }

  @Override
  public CredentialsVerification verify(String email, String password) {
    CredentialsCheck check = verifyCredentials.verify(email, password);
    var userId = check.user().map(u -> u.id().value()).orElse(null);
    if (!check.valid()) {
      return CredentialsVerification.failed(userId, check.outcome().name());
    }
    User user = check.user().orElseThrow();
    return CredentialsVerification.valid(
        new AuthenticatedUser(
            user.id().value(), user.email().value(), user.status() == UserStatus.ACTIVE));
  }
}

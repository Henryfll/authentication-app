package com.henryfll.auth.user.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henryfll.auth.shared.error.DomainException;
import com.henryfll.auth.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

  private static final Email EMAIL = new Email("maria.lopez@example.com");

  @Test
  void acceptsLongPassphrasesWithoutCompositionRules() {
    assertThatCode(() -> PasswordPolicy.validate("correct horse battery staple", EMAIL))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "short",
        "elevenchars",
        "Password1234",
        "aaaaaaaaaaaaaaaa",
        "my-maria.lopez-secret",
        "            "
      })
  void rejectsWeakPasswords(String password) {
    assertThatThrownBy(() -> PasswordPolicy.validate(password, EMAIL))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);
  }

  @Test
  void rejectsNullAndTooLongPasswords() {
    assertThatThrownBy(() -> PasswordPolicy.validate(null, EMAIL))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> PasswordPolicy.validate("x".repeat(129) + "yz", EMAIL))
        .isInstanceOf(DomainException.class);
  }
}

package com.henryfll.auth.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henryfll.auth.shared.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

  @Test
  void isNormalizedToLowercaseWithoutSpaces() {
    Email email = new Email("  John.Doe@Example.COM ");
    assertThat(email.value()).isEqualTo("john.doe@example.com");
    assertThat(email.localPart()).isEqualTo("john.doe");
    assertThat(email).isEqualTo(new Email("john.doe@example.com"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "plain", "@example.com", "john@", "john@example", "a b@example.com"})
  void rejectsInvalidAddresses(String value) {
    assertThatThrownBy(() -> new Email(value)).isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsAddressesLongerThan254Characters() {
    String local = "a".repeat(250);
    assertThatThrownBy(() -> new Email(local + "@example.com"))
        .isInstanceOf(ValidationException.class);
  }
}

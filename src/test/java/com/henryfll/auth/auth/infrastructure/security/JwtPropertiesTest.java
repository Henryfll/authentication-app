package com.henryfll.auth.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtPropertiesTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "${JWT_ISSUER}"})
  void refusesAMissingOrUnresolvedIssuer(String issuer) {
    assertThatThrownBy(() -> new JwtProperties(issuer, "k1", null, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_ISSUER is required");
  }

  @org.junit.jupiter.api.Test
  void acceptsAnIssuerUrl() {
    assertThatCode(() -> new JwtProperties("https://auth.example.com", "k1", null, false))
        .doesNotThrowAnyException();
    assertThat(new JwtProperties("https://auth.example.com", "k1", "SECRET", false).toString())
        .doesNotContain("SECRET");
  }
}

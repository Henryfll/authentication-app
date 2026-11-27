package com.henryfll.auth.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtConfigTest {

  private final JwtConfig config = new JwtConfig();

  @Test
  void refusesToStartWithoutAKeyWhenEphemeralKeysAreNotAllowed() {
    assertThatThrownBy(
            () -> config.jwtSigningKey(new JwtProperties("https://auth", "k1", " ", false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_PRIVATE_KEY is required");
  }

  @Test
  void usesTheConfiguredKeyWithItsKeyId() throws Exception {
    var pair = RsaKeys.generate();
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";

    RSAKey key = config.jwtSigningKey(new JwtProperties("https://auth", "prod-2026", pem, false));

    assertThat(key.getKeyID()).isEqualTo("prod-2026");
    assertThat(key.toRSAPublicKey()).isEqualTo(pair.getPublic());
    assertThat(key.isPrivate()).isTrue();
    assertThat(key.toPublicJWK().isPrivate()).isFalse();
  }

  @Test
  void propertiesNeverPrintThePrivateKey() {
    assertThat(new JwtProperties("i", "k", "SECRET-PEM", false).toString())
        .doesNotContain("SECRET-PEM");
  }
}

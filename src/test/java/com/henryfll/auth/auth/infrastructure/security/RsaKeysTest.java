package com.henryfll.auth.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RsaKeysTest {

  private static String pem(KeyPair pair, boolean escapedNewlines) {
    String body =
        Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded());
    String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    return escapedNewlines ? pem.replace("\n", "\\n") : pem;
  }

  @Test
  void loadsAPkcs8PemAndDerivesThePublicKey() {
    KeyPair generated = RsaKeys.generate();
    KeyPair loaded = RsaKeys.fromPkcs8Pem(pem(generated, false));
    assertThat(loaded.getPublic()).isEqualTo(generated.getPublic());
    assertThat(loaded.getPrivate().getEncoded()).isEqualTo(generated.getPrivate().getEncoded());
  }

  @Test
  void acceptsPemsWithEscapedNewlinesAsUsedInEnvironmentVariables() {
    KeyPair generated = RsaKeys.generate();
    assertThat(RsaKeys.fromPkcs8Pem(pem(generated, true)).getPublic())
        .isEqualTo(generated.getPublic());
  }

  @Test
  void rejectsPkcs1AndInvalidKeys() {
    assertThatThrownBy(() ->
            RsaKeys.fromPkcs8Pem(
                "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PKCS#8");
    assertThatThrownBy(() -> RsaKeys.fromPkcs8Pem("not a key"))
        .isInstanceOf(IllegalStateException.class);
  }
}

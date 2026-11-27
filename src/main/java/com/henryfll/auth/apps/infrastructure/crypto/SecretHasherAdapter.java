package com.henryfll.auth.apps.infrastructure.crypto;

import com.henryfll.auth.apps.application.port.out.SecretHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class SecretHasherAdapter implements SecretHasher {

  private final PasswordEncoder encoder;

  SecretHasherAdapter(PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  public String hash(String rawSecret) {
    return encoder.encode(rawSecret);
  }
}

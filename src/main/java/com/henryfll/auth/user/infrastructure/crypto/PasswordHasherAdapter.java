package com.henryfll.auth.user.infrastructure.crypto;

import com.henryfll.auth.user.application.port.out.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class PasswordHasherAdapter implements PasswordHasher {

  private final PasswordEncoder encoder;

  PasswordHasherAdapter(PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String hash) {
    return encoder.matches(rawPassword, hash);
  }
}

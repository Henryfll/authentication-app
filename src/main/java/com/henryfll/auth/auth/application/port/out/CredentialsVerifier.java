package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.CredentialsVerification;

public interface CredentialsVerifier {

  CredentialsVerification verify(String email, String password);
}

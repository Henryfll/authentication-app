package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.LoginClient;
import java.util.Optional;

public interface ClientDirectory {

  Optional<LoginClient> findByClientId(String clientId);
}

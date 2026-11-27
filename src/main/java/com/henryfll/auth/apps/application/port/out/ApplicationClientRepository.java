package com.henryfll.auth.apps.application.port.out;

import com.henryfll.auth.apps.domain.ApplicationClient;
import com.henryfll.auth.apps.domain.ApplicationClientId;
import com.henryfll.auth.apps.domain.ApplicationId;
import java.util.List;
import java.util.Optional;

public interface ApplicationClientRepository {

  ApplicationClient save(ApplicationClient client);

  Optional<ApplicationClient> findById(ApplicationClientId id);

  List<ApplicationClient> findByApplication(ApplicationId applicationId);

  boolean existsByClientId(String clientId);
}

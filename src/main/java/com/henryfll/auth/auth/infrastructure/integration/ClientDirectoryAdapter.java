package com.henryfll.auth.auth.infrastructure.integration;

import com.henryfll.auth.apps.application.port.in.ManageApplicationsUseCase;
import com.henryfll.auth.apps.application.port.in.ManageClientsUseCase;
import com.henryfll.auth.apps.domain.Application;
import com.henryfll.auth.auth.application.port.out.ClientDirectory;
import com.henryfll.auth.auth.domain.LoginClient;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ClientDirectoryAdapter implements ClientDirectory {

  private final ManageClientsUseCase clients;
  private final ManageApplicationsUseCase applications;

  ClientDirectoryAdapter(ManageClientsUseCase clients, ManageApplicationsUseCase applications) {
    this.clients = clients;
    this.applications = applications;
  }

  @Override
  public Optional<LoginClient> findByClientId(String clientId) {
    return clients
        .findByClientId(clientId)
        .map(client -> {
          Application app = applications.get(client.applicationId());
          return new LoginClient(
              client.clientId(),
              app.id().value(),
              app.code().value(),
              client.platform().name(),
              client.isConfidential(),
              client.isActive() && app.isActive(),
              client.accessTokenTtl());
        });
  }
}

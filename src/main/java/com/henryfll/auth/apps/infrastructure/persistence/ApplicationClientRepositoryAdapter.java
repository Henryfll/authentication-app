package com.henryfll.auth.apps.infrastructure.persistence;

import com.henryfll.auth.apps.application.port.out.ApplicationClientRepository;
import com.henryfll.auth.apps.domain.ActivationStatus;
import com.henryfll.auth.apps.domain.ApplicationClient;
import com.henryfll.auth.apps.domain.ApplicationClientId;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.ClientPlatform;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ApplicationClientRepositoryAdapter implements ApplicationClientRepository {

  private final ApplicationClientJpaRepository clients;

  ApplicationClientRepositoryAdapter(ApplicationClientJpaRepository clients) {
    this.clients = clients;
  }

  @Override
  public ApplicationClient save(ApplicationClient client) {
    ApplicationClientJpaEntity e =
        clients
            .findById(client.id().value())
            .orElseGet(() -> new ApplicationClientJpaEntity(client.id().value()));
    e.applicationId = client.applicationId().value();
    e.clientId = client.clientId();
    e.name = client.name();
    e.platform = client.platform().name();
    e.confidential = client.isConfidential();
    e.clientSecretHash = client.secretHash();
    e.allowedOrigins = new ArrayList<>(client.allowedOrigins());
    e.accessTokenTtlSeconds = Math.toIntExact(client.accessTokenTtl().toSeconds());
    e.refreshTokenTtlSeconds = Math.toIntExact(client.refreshTokenTtl().toSeconds());
    e.status = client.status().name();
    e.createdAt = client.createdAt();
    e.updatedAt = client.updatedAt();
    return toDomain(clients.saveAndFlush(e));
  }

  @Override
  public Optional<ApplicationClient> findById(ApplicationClientId id) {
    return clients.findById(id.value()).map(ApplicationClientRepositoryAdapter::toDomain);
  }

  @Override
  public List<ApplicationClient> findByApplication(ApplicationId applicationId) {
    return clients.findByApplicationIdOrderByCreatedAt(applicationId.value()).stream()
        .map(ApplicationClientRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByClientId(String clientId) {
    return clients.existsByClientId(clientId);
  }

  private static ApplicationClient toDomain(ApplicationClientJpaEntity e) {
    return ApplicationClient.restore(
        new ApplicationClientId(e.id),
        new ApplicationId(e.applicationId),
        e.clientId,
        e.name,
        ClientPlatform.valueOf(e.platform),
        e.clientSecretHash,
        e.allowedOrigins == null ? List.of() : List.copyOf(e.allowedOrigins),
        Duration.ofSeconds(e.accessTokenTtlSeconds),
        Duration.ofSeconds(e.refreshTokenTtlSeconds),
        ActivationStatus.valueOf(e.status),
        e.createdAt,
        e.updatedAt);
  }
}

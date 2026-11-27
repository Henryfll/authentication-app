package com.henryfll.auth.apps.application.service;

import com.henryfll.auth.apps.application.port.in.ManageClientsUseCase;
import com.henryfll.auth.apps.application.port.out.ApplicationClientRepository;
import com.henryfll.auth.apps.application.port.out.ApplicationRepository;
import com.henryfll.auth.apps.application.port.out.SecretHasher;
import com.henryfll.auth.apps.domain.Application;
import com.henryfll.auth.apps.domain.ApplicationClient;
import com.henryfll.auth.apps.domain.ApplicationClientId;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.ClientPlatform;
import com.henryfll.auth.shared.audit.AuditEvent;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.audit.AuditLog;
import com.henryfll.auth.shared.error.ConflictException;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ApplicationClientService implements ManageClientsUseCase {

  private final ApplicationRepository applications;
  private final ApplicationClientRepository clients;
  private final SecretHasher secretHasher;
  private final AuditLog audit;
  private final Clock clock;

  ApplicationClientService(
      ApplicationRepository applications,
      ApplicationClientRepository clients,
      SecretHasher secretHasher,
      AuditLog audit,
      Clock clock) {
    this.applications = applications;
    this.clients = clients;
    this.secretHasher = secretHasher;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  public ClientWithSecret create(ApplicationId applicationId, CreateClientCommand command) {
    Application application = requireApplication(applicationId);
    String clientId =
        command.clientId() != null
            ? command.clientId().strip().toLowerCase(Locale.ROOT)
            : generateClientId(application, command.platform());
    if (clients.existsByClientId(clientId)) {
      throw new ConflictException(
          ErrorCode.CLIENT_ID_ALREADY_EXISTS, "A client with id '" + clientId + "' already exists");
    }
    String plainSecret =
        command.platform() != null && command.platform().isConfidential()
            ? ClientSecrets.newSecret()
            : null;
    ApplicationClient client =
        ApplicationClient.create(
            applicationId,
            clientId,
            command.name(),
            command.platform(),
            plainSecret == null ? null : secretHasher.hash(plainSecret),
            command.allowedOrigins(),
            command.accessTokenTtl(),
            command.refreshTokenTtl(),
            clock.instant());
    ApplicationClient saved = clients.save(client);
    audit.record(clientEvent(AuditEventType.CLIENT_CREATED, saved));
    return new ClientWithSecret(saved, plainSecret);
  }

  @Override
  public ApplicationClient update(
      ApplicationId applicationId, ApplicationClientId clientId, UpdateClientCommand command) {
    ApplicationClient client = requireClient(applicationId, clientId);
    Instant now = clock.instant();
    if (command.name() != null) {
      client.rename(command.name(), now);
    }
    if (command.allowedOrigins() != null) {
      client.changeAllowedOrigins(command.allowedOrigins(), now);
    }
    if (command.accessTokenTtl() != null || command.refreshTokenTtl() != null) {
      client.changeTokenTtls(command.accessTokenTtl(), command.refreshTokenTtl(), now);
    }
    if (command.status() != null) {
      client.changeStatus(command.status(), now);
    }
    ApplicationClient saved = clients.save(client);
    audit.record(clientEvent(AuditEventType.CLIENT_UPDATED, saved).with("status", saved.status()));
    return saved;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ApplicationClient> list(ApplicationId applicationId) {
    requireApplication(applicationId);
    return clients.findByApplication(applicationId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ApplicationClient> findById(ApplicationClientId id) {
    return clients.findById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ApplicationClient> findByClientId(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      return Optional.empty();
    }
    return clients.findByClientId(clientId.strip().toLowerCase(Locale.ROOT));
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> activeWebOrigins() {
    List<ApplicationClient> webClients = clients.findActiveByPlatform(ClientPlatform.WEB);
    Set<ApplicationId> activeApps =
        webClients.stream()
            .map(ApplicationClient::applicationId)
            .distinct()
            .filter(id -> applications.findById(id).map(Application::isActive).orElse(false))
            .collect(Collectors.toSet());
    return webClients.stream()
        .filter(c -> activeApps.contains(c.applicationId()))
        .flatMap(c -> c.allowedOrigins().stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public ClientWithSecret rotateSecret(ApplicationId applicationId, ApplicationClientId clientId) {
    ApplicationClient client = requireClient(applicationId, clientId);
    String plainSecret = ClientSecrets.newSecret();
    client.rotateSecret(secretHasher.hash(plainSecret), clock.instant());
    ApplicationClient saved = clients.save(client);
    audit.record(clientEvent(AuditEventType.CLIENT_SECRET_ROTATED, saved));
    return new ClientWithSecret(saved, plainSecret);
  }

  private static AuditEvent clientEvent(AuditEventType type, ApplicationClient client) {
    return AuditEvent.of(type, null, client.applicationId().value())
        .with("clientId", client.clientId())
        .with("platform", client.platform());
  }

  private String generateClientId(Application application, ClientPlatform platform) {
    String prefix =
        application.code().value()
            + "-"
            + (platform == null ? "client" : platform.name().toLowerCase(Locale.ROOT));
    String candidate = prefix;
    while (clients.existsByClientId(candidate)) {
      candidate = prefix + "-" + ClientSecrets.newSuffix();
    }
    return candidate;
  }

  private Application requireApplication(ApplicationId id) {
    return applications
        .findById(id)
        .orElseThrow(() -> new NotFoundException(ErrorCode.APPLICATION_NOT_FOUND, id));
  }

  private ApplicationClient requireClient(ApplicationId applicationId, ApplicationClientId id) {
    requireApplication(applicationId);
    return clients
        .findById(id)
        .filter(c -> c.applicationId().equals(applicationId))
        .orElseThrow(() -> new NotFoundException(ErrorCode.CLIENT_NOT_FOUND, id));
  }
}

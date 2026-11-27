package com.henryfll.auth.access.infrastructure.bootstrap;

import com.henryfll.auth.access.application.port.in.ManageAccessUseCase;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase.GrantAccessCommand;
import com.henryfll.auth.access.domain.AccessStatus;
import com.henryfll.auth.apps.domain.AuthAdminApplication;
import com.henryfll.auth.shared.error.DomainException;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase.CreateUserCommand;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.domain.User;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea el primer {@code SUPER_ADMIN} de {@code auth-admin} al arrancar, si se configuró y aún no
 * existe. Es idempotente: si el usuario ya existe no cambia su contraseña; solo le concede el
 * acceso si no lo tiene.
 */
@Component
class BootstrapAdminRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

  private final BootstrapAdminProperties properties;
  private final FindUsersUseCase findUsers;
  private final CreateUserUseCase createUser;
  private final ManageAccessUseCase access;

  BootstrapAdminRunner(
      BootstrapAdminProperties properties,
      FindUsersUseCase findUsers,
      CreateUserUseCase createUser,
      ManageAccessUseCase access) {
    this.properties = properties;
    this.findUsers = findUsers;
    this.createUser = createUser;
    this.access = access;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.enabled()) {
      log.info("Bootstrap admin not configured (AUTH_BOOTSTRAP_ADMIN_EMAIL is empty); skipping");
      return;
    }
    User admin = findUsers.findByEmail(properties.email()).orElseGet(this::createAdmin);

    UUID appId = AuthAdminApplication.ID.value();
    UUID userId = admin.id().value();
    boolean hasAccess =
        access.listByUser(userId).stream().anyMatch(view -> view.application().id().equals(appId));
    if (!hasAccess) {
      access.grant(
          appId,
          userId,
          new GrantAccessCommand(
              AccessStatus.ACTIVE,
              Set.of(AuthAdminApplication.SUPER_ADMIN_ROLE.value()),
              null,
              null,
              null));
      log.info("Granted SUPER_ADMIN on auth-admin to {}", properties.email());
    }
  }

  private User createAdmin() {
    log.info("Creating bootstrap admin user {}", properties.email());
    try {
      return createUser.create(
          new CreateUserCommand(properties.email(), properties.password(), "Super", "Admin"));
    } catch (DomainException e) {
      throw new IllegalStateException(
          "Invalid bootstrap admin configuration (AUTH_BOOTSTRAP_ADMIN_EMAIL /"
              + " AUTH_BOOTSTRAP_ADMIN_PASSWORD): "
              + e.getMessage(),
          e);
    }
  }
}

package com.henryfll.auth.apps.application.service;

import com.henryfll.auth.apps.application.port.in.ManageApplicationsUseCase;
import com.henryfll.auth.apps.application.port.out.ApplicationRepository;
import com.henryfll.auth.apps.application.port.out.RoleRepository;
import com.henryfll.auth.apps.domain.Application;
import com.henryfll.auth.apps.domain.ApplicationCode;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Role;
import com.henryfll.auth.shared.audit.AuditEvent;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.audit.AuditLog;
import com.henryfll.auth.shared.error.ConflictException;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.NotFoundException;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ApplicationService implements ManageApplicationsUseCase {

  private final ApplicationRepository applications;
  private final RoleRepository roles;
  private final AuditLog audit;
  private final Clock clock;

  ApplicationService(
      ApplicationRepository applications, RoleRepository roles, AuditLog audit, Clock clock) {
    this.applications = applications;
    this.roles = roles;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  public Application create(CreateApplicationCommand command) {
    ApplicationCode code = new ApplicationCode(command.code());
    if (applications.existsByCode(code)) {
      throw new ConflictException(
          ErrorCode.APPLICATION_CODE_ALREADY_EXISTS,
          "An application with code '" + code + "' already exists");
    }
    Application app =
        applications.save(
            Application.create(code, command.name(), command.description(), clock.instant()));
    audit.record(
        AuditEvent.of(AuditEventType.APPLICATION_CREATED, null, app.id().value())
            .with("code", app.code().value()));
    return app;
  }

  @Override
  public Application update(ApplicationId id, UpdateApplicationCommand command) {
    Application application = get(id);
    Instant now = clock.instant();
    if (command.name() != null) {
      application.rename(command.name(), now);
    }
    if (command.description() != null) {
      application.describe(command.description(), now);
    }
    if (command.status() != null) {
      application.changeStatus(command.status(), now);
    }
    if (command.defaultRoleId() != null) {
      Role role =
          roles
              .findById(command.defaultRoleId())
              .filter(r -> r.applicationId().equals(id))
              .orElseThrow(
                  () -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND, command.defaultRoleId()));
      application.assignDefaultRole(role, now);
    }
    if (command.selfRegistration() != null) {
      application.changeSelfRegistration(command.selfRegistration(), now);
    }
    Application saved = applications.save(application);
    audit.record(
        AuditEvent.of(AuditEventType.APPLICATION_UPDATED, null, saved.id().value())
            .with("status", saved.status())
            .with("selfRegistration", saved.selfRegistration()));
    return saved;
  }

  @Override
  @Transactional(readOnly = true)
  public Application get(ApplicationId id) {
    return applications
        .findById(id)
        .orElseThrow(() -> new NotFoundException(ErrorCode.APPLICATION_NOT_FOUND, id));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<Application> list(PageQuery page) {
    return applications.findAll(page);
  }
}

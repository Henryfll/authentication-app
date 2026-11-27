package com.henryfll.auth.apps.infrastructure.persistence;

import com.henryfll.auth.apps.application.port.out.ApplicationRepository;
import com.henryfll.auth.apps.domain.ActivationStatus;
import com.henryfll.auth.apps.domain.Application;
import com.henryfll.auth.apps.domain.ApplicationCode;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.RoleId;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class ApplicationRepositoryAdapter implements ApplicationRepository {

  private final ApplicationJpaRepository applications;

  ApplicationRepositoryAdapter(ApplicationJpaRepository applications) {
    this.applications = applications;
  }

  @Override
  public Application save(Application app) {
    ApplicationJpaEntity e =
        applications
            .findById(app.id().value())
            .orElseGet(() -> new ApplicationJpaEntity(app.id().value()));
    e.code = app.code().value();
    e.name = app.name();
    e.description = app.description();
    e.status = app.status().name();
    e.selfRegistration = app.selfRegistration();
    e.defaultRoleId = app.defaultRoleId() == null ? null : app.defaultRoleId().value();
    e.createdAt = app.createdAt();
    e.updatedAt = app.updatedAt();
    return toDomain(applications.saveAndFlush(e));
  }

  @Override
  public Optional<Application> findById(ApplicationId id) {
    return applications.findById(id.value()).map(ApplicationRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByCode(ApplicationCode code) {
    return applications.existsByCode(code.value());
  }

  @Override
  public PageResult<Application> findAll(PageQuery page) {
    Page<ApplicationJpaEntity> result =
        applications.findAll(PageRequest.of(page.page(), page.size(), Sort.by("code")));
    return new PageResult<>(
        result.getContent().stream().map(ApplicationRepositoryAdapter::toDomain).toList(),
        page.page(),
        page.size(),
        result.getTotalElements());
  }

  private static Application toDomain(ApplicationJpaEntity e) {
    return Application.restore(
        new ApplicationId(e.id),
        new ApplicationCode(e.code),
        e.name,
        e.description,
        ActivationStatus.valueOf(e.status),
        e.selfRegistration,
        e.defaultRoleId == null ? null : new RoleId(e.defaultRoleId),
        e.createdAt,
        e.updatedAt);
  }
}

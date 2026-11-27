package com.henryfll.auth.access.application.service;

import com.henryfll.auth.access.application.port.in.ListMyApplicationsUseCase;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase;
import com.henryfll.auth.access.application.port.in.ResolveAuthorizationUseCase;
import com.henryfll.auth.access.application.port.out.AccessGrantRepository;
import com.henryfll.auth.access.application.port.out.ApplicationCatalog;
import com.henryfll.auth.access.application.port.out.UserDirectory;
import com.henryfll.auth.access.domain.AccessGrant;
import com.henryfll.auth.access.domain.AccessStatus;
import com.henryfll.auth.access.domain.AccessWithdrawnEvent;
import com.henryfll.auth.access.domain.ApplicationRef;
import com.henryfll.auth.access.domain.RoleRef;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.NotFoundException;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class AccessService
    implements ManageAccessUseCase, ListMyApplicationsUseCase, ResolveAuthorizationUseCase {

  private final AccessGrantRepository grants;
  private final UserDirectory userDirectory;
  private final ApplicationCatalog catalog;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  AccessService(
      AccessGrantRepository grants,
      UserDirectory userDirectory,
      ApplicationCatalog catalog,
      Clock clock,
      ApplicationEventPublisher events) {
    this.grants = grants;
    this.userDirectory = userDirectory;
    this.catalog = catalog;
    this.clock = clock;
    this.events = events;
  }

  @Override
  public GrantResult grant(UUID applicationId, UUID userId, GrantAccessCommand command) {
    ApplicationRef application = requireApplication(applicationId);
    if (!userDirectory.exists(userId)) {
      throw new NotFoundException(ErrorCode.USER_NOT_FOUND, userId);
    }
    Map<UUID, RoleRef> roles = rolesById(applicationId);
    Set<UUID> roleIds = command.roleIds() == null ? Set.of() : command.roleIds();
    roleIds.stream()
        .filter(id -> !roles.containsKey(id))
        .findFirst()
        .ifPresent(missing -> {
          throw new NotFoundException(ErrorCode.ROLE_NOT_FOUND, missing);
        });

    Instant now = clock.instant();
    var existing = grants.find(userId, applicationId);
    AccessGrant grant;
    if (existing.isPresent()) {
      grant = existing.get();
      grant.replace(
          command.status(),
          roleIds,
          command.validFrom(),
          command.validUntil(),
          command.grantedBy(),
          now);
    } else {
      grant =
          AccessGrant.grant(
              userId,
              applicationId,
              command.status(),
              roleIds,
              command.validFrom(),
              command.validUntil(),
              command.grantedBy(),
              now);
    }
    AccessGrant saved = grants.save(grant);
    if (saved.status() != AccessStatus.ACTIVE) {
      events.publishEvent(new AccessWithdrawnEvent(userId, applicationId, saved.status()));
    }
    return new GrantResult(view(saved, application, roles, now), existing.isEmpty());
  }

  @Override
  public void revoke(UUID applicationId, UUID userId) {
    AccessGrant grant = requireGrant(applicationId, userId);
    grant.revoke(clock.instant());
    grants.save(grant);
    events.publishEvent(new AccessWithdrawnEvent(userId, applicationId, AccessStatus.REVOKED));
  }

  @Override
  @Transactional(readOnly = true)
  public AccessView get(UUID applicationId, UUID userId) {
    ApplicationRef application = requireApplication(applicationId);
    return view(
        requireGrant(applicationId, userId),
        application,
        rolesById(applicationId),
        clock.instant());
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<AccessView> listByApplication(
      UUID applicationId, AccessStatus status, PageQuery page) {
    ApplicationRef application = requireApplication(applicationId);
    Map<UUID, RoleRef> roles = rolesById(applicationId);
    Instant now = clock.instant();
    return grants
        .findByApplication(applicationId, status, page)
        .map(grant -> view(grant, application, roles, now));
  }

  @Override
  @Transactional(readOnly = true)
  public List<AccessView> listByUser(UUID userId) {
    if (!userDirectory.exists(userId)) {
      throw new NotFoundException(ErrorCode.USER_NOT_FOUND, userId);
    }
    Instant now = clock.instant();
    Map<UUID, ApplicationRef> applications = new HashMap<>();
    Map<UUID, Map<UUID, RoleRef>> roles = new HashMap<>();
    return grants.findByUser(userId).stream()
        .map(grant -> {
          UUID appId = grant.applicationId();
          ApplicationRef app = applications.computeIfAbsent(appId, this::requireApplication);
          return view(grant, app, roles.computeIfAbsent(appId, this::rolesById), now);
        })
        .sorted(Comparator.comparing(v -> v.application().code()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<MyApplication> listEffective(UUID userId) {
    Instant now = clock.instant();
    return grants.findByUser(userId).stream()
        .filter(grant -> grant.isEffectiveAt(now))
        .map(grant -> {
          ApplicationRef app = catalog.findApplication(grant.applicationId()).orElse(null);
          if (app == null || !app.active()) {
            return null;
          }
          Map<UUID, RoleRef> roles = rolesById(app.id());
          List<String> roleCodes =
              grant.roleIds().stream()
                  .map(roles::get)
                  .filter(Objects::nonNull)
                  .map(RoleRef::code)
                  .sorted()
                  .toList();
          return new MyApplication(app, roleCodes);
        })
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(m -> m.application().code()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EffectiveAuthorization> resolve(UUID userId, UUID applicationId) {
    Instant now = clock.instant();
    Optional<AccessGrant> grant =
        grants.find(userId, applicationId).filter(g -> g.isEffectiveAt(now));
    if (grant.isEmpty()) {
      return Optional.empty();
    }
    Optional<ApplicationRef> app =
        catalog.findApplication(applicationId).filter(ApplicationRef::active);
    if (app.isEmpty()) {
      return Optional.empty();
    }
    Map<UUID, RoleRef> roles = rolesById(applicationId);
    List<RoleRef> assigned =
        grant.get().roleIds().stream().map(roles::get).filter(Objects::nonNull).toList();
    return Optional.of(
        new EffectiveAuthorization(
            applicationId,
            app.get().code(),
            assigned.stream().map(RoleRef::code).sorted().toList(),
            assigned.stream().flatMap(r -> r.permissions().stream()).distinct().sorted().toList()));
  }

  private static AccessView view(
      AccessGrant grant, ApplicationRef application, Map<UUID, RoleRef> roles, Instant now) {
    List<RoleRef> grantRoles =
        grant.roleIds().stream()
            .map(roles::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(RoleRef::code))
            .toList();
    return new AccessView(
        grant, application, grantRoles, grant.isEffectiveAt(now) && application.active());
  }

  private ApplicationRef requireApplication(UUID applicationId) {
    return catalog
        .findApplication(applicationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));
  }

  private Map<UUID, RoleRef> rolesById(UUID applicationId) {
    return catalog.rolesOf(applicationId).stream()
        .collect(Collectors.toMap(RoleRef::id, Function.identity()));
  }

  private AccessGrant requireGrant(UUID applicationId, UUID userId) {
    return grants
        .find(userId, applicationId)
        .orElseThrow(
            () -> new NotFoundException(ErrorCode.ACCESS_NOT_FOUND, userId + "@" + applicationId));
  }
}

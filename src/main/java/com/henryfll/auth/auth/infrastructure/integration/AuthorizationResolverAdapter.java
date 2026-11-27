package com.henryfll.auth.auth.infrastructure.integration;

import com.henryfll.auth.access.application.port.in.ResolveAuthorizationUseCase;
import com.henryfll.auth.auth.application.port.out.AuthorizationResolver;
import com.henryfll.auth.auth.domain.Authorization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AuthorizationResolverAdapter implements AuthorizationResolver {

  private final ResolveAuthorizationUseCase resolver;

  AuthorizationResolverAdapter(ResolveAuthorizationUseCase resolver) {
    this.resolver = resolver;
  }

  @Override
  public Optional<Authorization> resolve(UUID userId, UUID applicationId) {
    return resolver
        .resolve(userId, applicationId)
        .map(a -> new Authorization(a.applicationCode(), a.roles(), a.permissions()));
  }
}

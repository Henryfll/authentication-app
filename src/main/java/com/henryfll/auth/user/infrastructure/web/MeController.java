package com.henryfll.auth.user.infrastructure.web;

import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil del usuario autenticado (válido con un token de cualquier aplicación). */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Me")
@SecurityRequirement(name = "bearer")
class MeController {

  private final FindUsersUseCase users;

  MeController(FindUsersUseCase users) {
    this.users = users;
  }

  @GetMapping
  @Operation(summary = "Perfil del usuario autenticado")
  MeResponse me(@AuthenticationPrincipal Jwt jwt) {
    User user = users.get(new UserId(UUID.fromString(jwt.getSubject())));
    return new MeResponse(
        user.id().value(),
        user.email().value(),
        user.firstName(),
        user.lastName(),
        user.status(),
        user.isEmailVerified(),
        user.createdAt());
  }

  record MeResponse(
      UUID id,
      String email,
      String firstName,
      String lastName,
      UserStatus status,
      boolean emailVerified,
      Instant createdAt) {}
}

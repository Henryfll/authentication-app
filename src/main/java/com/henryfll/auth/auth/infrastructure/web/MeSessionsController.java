package com.henryfll.auth.auth.infrastructure.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.auth.application.port.in.ManageSessionsUseCase;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.web.ErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sesiones y dispositivos activos del usuario autenticado. */
@RestController
@RequestMapping("/api/v1/users/me/sessions")
@Tag(name = "Me")
class MeSessionsController {

  private final ManageSessionsUseCase sessions;

  MeSessionsController(ManageSessionsUseCase sessions) {
    this.sessions = sessions;
  }

  @GetMapping
  @Operation(
      summary = "Sesiones activas del usuario",
      description = "`current` marca la sesión del access token usado en la petición.")
  List<SessionResponse> list(@AuthenticationPrincipal Jwt jwt) {
    String currentSid = jwt.getClaimAsString("sid");
    return sessions.listActive(UUID.fromString(jwt.getSubject())).stream()
        .map(view -> {
          var s = view.session();
          return new SessionResponse(
              s.id(),
              view.applicationCode(),
              view.clientId(),
              view.platform(),
              s.deviceName(),
              s.ipAddress(),
              s.userAgent(),
              s.createdAt(),
              s.lastUsedAt(),
              s.id().toString().equals(currentSid));
        })
        .toList();
  }

  @DeleteMapping("/{sessionId}")
  @ApiResponse(responseCode = "204", description = "Sesión cerrada")
  @ErrorResponses({ErrorCode.SESSION_NOT_FOUND})
  @Operation(summary = "Cerrar una sesión concreta (p. ej. un dispositivo perdido)")
  ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
    sessions.revokeOwn(UUID.fromString(jwt.getSubject()), sessionId);
    return ResponseEntity.noContent().build();
  }

  record SessionResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED, nullable = true) String application,
      @Schema(requiredMode = REQUIRED, nullable = true) String clientId,
      @Schema(requiredMode = REQUIRED, nullable = true) String platform,
      @Schema(requiredMode = REQUIRED, nullable = true) String deviceName,
      @Schema(requiredMode = REQUIRED, nullable = true) String ipAddress,
      @Schema(requiredMode = REQUIRED, nullable = true) String userAgent,
      @Schema(requiredMode = REQUIRED) Instant createdAt,
      @Schema(requiredMode = REQUIRED) Instant lastUsedAt,
      @Schema(requiredMode = REQUIRED) boolean current) {}
}

package com.henryfll.auth.access.infrastructure.web;

import com.henryfll.auth.access.application.port.in.ManageAccessUseCase;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase.AccessView;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase.GrantAccessCommand;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase.GrantResult;
import com.henryfll.auth.access.domain.AccessStatus;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin · Access")
class AdminAccessController {

  private final ManageAccessUseCase access;

  AdminAccessController(ManageAccessUseCase access) {
    this.access = access;
  }

  @PutMapping("/applications/{appId}/access/{userId}")
  @Operation(
      summary = "Conceder o actualizar el acceso de un usuario a una aplicación",
      description =
          "Reemplaza la configuración completa del acceso: estado (por defecto ACTIVE),"
              + " vigencia y roles. `validUntil` vacío significa que no caduca. Responde 201 si el"
              + " acceso es nuevo y 200 si ya existía.")
  ResponseEntity<AccessResponse> grant(
      @PathVariable UUID appId,
      @PathVariable UUID userId,
      @Valid @RequestBody GrantAccessRequest request) {
    GrantResult result =
        access.grant(
            appId,
            userId,
            new GrantAccessCommand(
                request.status(),
                request.roleIds(),
                request.validFrom(),
                request.validUntil(),
                null));
    AccessResponse body = AccessResponse.from(result.access());
    if (!result.created()) {
      return ResponseEntity.ok(body);
    }
    return ResponseEntity.created(
            URI.create("/api/v1/admin/applications/" + appId + "/access/" + userId))
        .body(body);
  }

  @GetMapping("/applications/{appId}/access/{userId}")
  @Operation(summary = "Obtener el acceso de un usuario a una aplicación")
  AccessResponse get(@PathVariable UUID appId, @PathVariable UUID userId) {
    return AccessResponse.from(access.get(appId, userId));
  }

  @DeleteMapping("/applications/{appId}/access/{userId}")
  @Operation(
      summary = "Revocar el acceso",
      description = "El acceso queda en estado REVOKED; se puede volver a conceder con PUT.")
  ResponseEntity<Void> revoke(@PathVariable UUID appId, @PathVariable UUID userId) {
    access.revoke(appId, userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/applications/{appId}/access")
  @Operation(summary = "Usuarios con acceso a una aplicación (paginado)")
  PageResponse<AccessResponse> listByApplication(
      @PathVariable UUID appId,
      @RequestParam(required = false) AccessStatus status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        access.listByApplication(appId, status, PageQuery.of(page, size)), AccessResponse::from);
  }

  @GetMapping("/users/{userId}/access")
  @Operation(summary = "Accesos de un usuario a todas las aplicaciones")
  List<AccessResponse> listByUser(@PathVariable UUID userId) {
    return access.listByUser(userId).stream().map(AccessResponse::from).toList();
  }

  record GrantAccessRequest(
      AccessStatus status,
      @NotNull @Size(max = 100) Set<@NotNull UUID> roleIds,
      Instant validFrom,
      Instant validUntil) {}

  record RoleSummary(UUID id, String code) {}

  record ApplicationSummary(UUID id, String code, String name) {}

  record AccessResponse(
      UUID id,
      UUID userId,
      ApplicationSummary application,
      AccessStatus status,
      boolean effective,
      Instant validFrom,
      Instant validUntil,
      List<RoleSummary> roles,
      UUID grantedBy,
      Instant createdAt,
      Instant updatedAt) {

    static AccessResponse from(AccessView view) {
      var grant = view.grant();
      var app = view.application();
      return new AccessResponse(
          grant.id().value(),
          grant.userId(),
          new ApplicationSummary(app.id(), app.code(), app.name()),
          grant.status(),
          view.effective(),
          grant.validFrom(),
          grant.validUntil(),
          view.roles().stream().map(r -> new RoleSummary(r.id(), r.code())).toList(),
          grant.grantedBy(),
          grant.createdAt(),
          grant.updatedAt());
    }
  }
}

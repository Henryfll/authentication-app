package com.henryfll.auth.access.infrastructure.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.access.application.port.in.ManageAccessUseCase;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase.AccessView;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase.GrantAccessCommand;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase.GrantResult;
import com.henryfll.auth.access.domain.AccessStatus;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.web.ErrorResponses;
import com.henryfll.auth.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Acceso concedido por primera vez",
        headers =
            @Header(
                name = "Location",
                description = "URL del acceso",
                schema = @Schema(type = "string"))),
    @ApiResponse(responseCode = "200", description = "El acceso ya existía y se ha actualizado")
  })
  @ErrorResponses({
    ErrorCode.APPLICATION_NOT_FOUND,
    ErrorCode.USER_NOT_FOUND,
    ErrorCode.ROLE_NOT_FOUND,
    ErrorCode.CONFLICT
  })
  @Operation(
      operationId = "grantAccess",
      summary = "Conceder o actualizar el acceso de un usuario a una aplicación",
      description =
          "Reemplaza la configuración completa del acceso: estado (por defecto ACTIVE),"
              + " vigencia y roles. `validUntil` vacío significa que no caduca. Responde 201 si el"
              + " acceso es nuevo y 200 si ya existía.")
  ResponseEntity<AccessResponse> grant(
      @PathVariable UUID appId,
      @PathVariable UUID userId,
      @Valid @RequestBody GrantAccessRequest request,
      @AuthenticationPrincipal Jwt admin) {
    GrantResult result =
        access.grant(
            appId,
            userId,
            new GrantAccessCommand(
                request.status(),
                request.roleIds(),
                request.validFrom(),
                request.validUntil(),
                UUID.fromString(admin.getSubject())));
    AccessResponse body = AccessResponse.from(result.access());
    if (!result.created()) {
      return ResponseEntity.ok(body);
    }
    return ResponseEntity.created(
            URI.create("/api/v1/admin/applications/" + appId + "/access/" + userId))
        .body(body);
  }

  @GetMapping("/applications/{appId}/access/{userId}")
  @ErrorResponses({ErrorCode.APPLICATION_NOT_FOUND, ErrorCode.ACCESS_NOT_FOUND})
  @Operation(
      operationId = "getAccess",
      summary = "Obtener el acceso de un usuario a una aplicación")
  AccessResponse get(@PathVariable UUID appId, @PathVariable UUID userId) {
    return AccessResponse.from(access.get(appId, userId));
  }

  @DeleteMapping("/applications/{appId}/access/{userId}")
  @ApiResponse(responseCode = "204", description = "Acceso revocado (queda en estado REVOKED)")
  @ErrorResponses({ErrorCode.ACCESS_NOT_FOUND})
  @Operation(
      operationId = "revokeAccess",
      summary = "Revocar el acceso",
      description = "El acceso queda en estado REVOKED; se puede volver a conceder con PUT.")
  ResponseEntity<Void> revoke(@PathVariable UUID appId, @PathVariable UUID userId) {
    access.revoke(appId, userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/applications/{appId}/access")
  @ErrorResponses({ErrorCode.APPLICATION_NOT_FOUND})
  @Operation(
      operationId = "listApplicationAccess",
      summary = "Usuarios con acceso a una aplicación (paginado)")
  PageResponse<AccessResponse> listByApplication(
      @PathVariable UUID appId,
      @RequestParam(required = false) AccessStatus status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        access.listByApplication(appId, status, PageQuery.of(page, size)), AccessResponse::from);
  }

  @GetMapping("/users/{userId}/access")
  @ErrorResponses({ErrorCode.USER_NOT_FOUND})
  @Operation(
      operationId = "listUserAccess",
      summary = "Accesos de un usuario a todas las aplicaciones")
  List<AccessResponse> listByUser(@PathVariable UUID userId) {
    return access.listByUser(userId).stream().map(AccessResponse::from).toList();
  }

  record GrantAccessRequest(
      AccessStatus status,
      @NotNull @Size(max = 100) Set<@NotNull UUID> roleIds,
      Instant validFrom,
      Instant validUntil) {}

  record RoleSummary(
      @Schema(requiredMode = REQUIRED) UUID id, @Schema(requiredMode = REQUIRED) String code) {}

  record ApplicationSummary(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) String code,
      @Schema(requiredMode = REQUIRED) String name) {}

  record AccessResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) UUID userId,
      @Schema(requiredMode = REQUIRED) ApplicationSummary application,
      @Schema(requiredMode = REQUIRED) AccessStatus status,
      @Schema(requiredMode = REQUIRED) boolean effective,
      @Schema(requiredMode = REQUIRED) Instant validFrom,
      @Schema(requiredMode = REQUIRED, nullable = true) Instant validUntil,
      @Schema(requiredMode = REQUIRED) List<RoleSummary> roles,
      @Schema(requiredMode = REQUIRED, nullable = true) UUID grantedBy,
      @Schema(requiredMode = REQUIRED) Instant createdAt,
      @Schema(requiredMode = REQUIRED) Instant updatedAt) {

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

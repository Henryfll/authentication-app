package com.henryfll.auth.apps.infrastructure.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.apps.application.port.in.ManagePermissionsUseCase;
import com.henryfll.auth.apps.application.port.in.ManagePermissionsUseCase.CreatePermissionCommand;
import com.henryfll.auth.apps.application.port.in.ManageRolesUseCase;
import com.henryfll.auth.apps.application.port.in.ManageRolesUseCase.CreateRoleCommand;
import com.henryfll.auth.apps.application.port.in.ManageRolesUseCase.RoleView;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Permission;
import com.henryfll.auth.apps.domain.PermissionId;
import com.henryfll.auth.apps.domain.RoleId;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.web.ErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de permisos y roles de cada aplicación. */
@RestController
@RequestMapping("/api/v1/admin/applications/{appId}")
@Tag(name = "Admin · Roles & permissions")
class AdminAuthorizationController {

  private final ManagePermissionsUseCase permissions;
  private final ManageRolesUseCase roles;

  AdminAuthorizationController(ManagePermissionsUseCase permissions, ManageRolesUseCase roles) {
    this.permissions = permissions;
    this.roles = roles;
  }

  @PostMapping("/permissions")
  @ApiResponse(
      responseCode = "201",
      description = "Permiso creado",
      headers =
          @Header(
              name = "Location",
              description = "URL del recurso creado",
              schema = @Schema(type = "string")))
  @ErrorResponses({ErrorCode.APPLICATION_NOT_FOUND, ErrorCode.PERMISSION_ALREADY_EXISTS})
  @Operation(summary = "Crear un permiso")
  ResponseEntity<PermissionResponse> createPermission(
      @PathVariable UUID appId, @Valid @RequestBody CreatePermissionRequest request) {
    Permission permission =
        permissions.createPermission(
            new ApplicationId(appId),
            new CreatePermissionCommand(request.code(), request.description()));
    return ResponseEntity.created(
            URI.create("/api/v1/admin/applications/" + appId + "/permissions/" + permission.id()))
        .body(PermissionResponse.from(permission));
  }

  @GetMapping("/permissions")
  @ErrorResponses({ErrorCode.APPLICATION_NOT_FOUND})
  @Operation(summary = "Listar los permisos de una aplicación")
  List<PermissionResponse> listPermissions(@PathVariable UUID appId) {
    return permissions.listPermissions(new ApplicationId(appId)).stream()
        .map(PermissionResponse::from)
        .toList();
  }

  @PostMapping("/roles")
  @ApiResponse(
      responseCode = "201",
      description = "Rol creado",
      headers =
          @Header(
              name = "Location",
              description = "URL del recurso creado",
              schema = @Schema(type = "string")))
  @ErrorResponses({
    ErrorCode.APPLICATION_NOT_FOUND,
    ErrorCode.PERMISSION_NOT_FOUND,
    ErrorCode.ROLE_ALREADY_EXISTS,
    ErrorCode.BUSINESS_RULE_VIOLATION
  })
  @Operation(summary = "Crear un rol")
  ResponseEntity<RoleResponse> createRole(
      @PathVariable UUID appId, @Valid @RequestBody CreateRoleRequest request) {
    RoleView role =
        roles.createRole(
            new ApplicationId(appId),
            new CreateRoleCommand(
                request.code(),
                request.name(),
                request.description(),
                toPermissionIds(request.permissionIds())));
    return ResponseEntity.created(
            URI.create("/api/v1/admin/applications/" + appId + "/roles/" + role.role().id()))
        .body(RoleResponse.from(role));
  }

  @GetMapping("/roles")
  @ErrorResponses({ErrorCode.APPLICATION_NOT_FOUND})
  @Operation(summary = "Listar los roles de una aplicación con sus permisos")
  List<RoleResponse> listRoles(@PathVariable UUID appId) {
    return roles.listRoles(new ApplicationId(appId)).stream().map(RoleResponse::from).toList();
  }

  @GetMapping("/roles/{roleId}")
  @ErrorResponses({ErrorCode.APPLICATION_NOT_FOUND, ErrorCode.ROLE_NOT_FOUND})
  @Operation(summary = "Obtener un rol con sus permisos")
  RoleResponse getRole(@PathVariable UUID appId, @PathVariable UUID roleId) {
    return RoleResponse.from(roles.getRole(new ApplicationId(appId), new RoleId(roleId)));
  }

  @PutMapping("/roles/{roleId}/permissions")
  @ErrorResponses({
    ErrorCode.APPLICATION_NOT_FOUND,
    ErrorCode.ROLE_NOT_FOUND,
    ErrorCode.PERMISSION_NOT_FOUND,
    ErrorCode.BUSINESS_RULE_VIOLATION,
    ErrorCode.CONFLICT
  })
  @Operation(summary = "Definir el conjunto completo de permisos de un rol")
  RoleResponse replaceRolePermissions(
      @PathVariable UUID appId,
      @PathVariable UUID roleId,
      @Valid @RequestBody ReplacePermissionsRequest request) {
    return RoleResponse.from(
        roles.replacePermissions(
            new ApplicationId(appId),
            new RoleId(roleId),
            toPermissionIds(request.permissionIds())));
  }

  private static Set<PermissionId> toPermissionIds(Set<UUID> ids) {
    return ids == null
        ? Set.of()
        : ids.stream().map(PermissionId::new).collect(Collectors.toUnmodifiableSet());
  }

  record CreatePermissionRequest(
      @NotBlank @Size(max = 100) String code, @Size(max = 1000) String description) {}

  record CreateRoleRequest(
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 100) String name,
      @Size(max = 1000) String description,
      @Size(max = 500) Set<@NotNull UUID> permissionIds) {}

  record ReplacePermissionsRequest(@NotNull @Size(max = 500) Set<@NotNull UUID> permissionIds) {}

  record PermissionResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) String code,
      @Schema(requiredMode = REQUIRED, nullable = true) String description,
      @Schema(requiredMode = REQUIRED) Instant createdAt) {

    static PermissionResponse from(Permission p) {
      return new PermissionResponse(p.id().value(), p.code(), p.description(), p.createdAt());
    }
  }

  record RoleResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) UUID applicationId,
      @Schema(requiredMode = REQUIRED) String code,
      @Schema(requiredMode = REQUIRED) String name,
      @Schema(requiredMode = REQUIRED, nullable = true) String description,
      @Schema(requiredMode = REQUIRED) List<PermissionResponse> permissions,
      @Schema(requiredMode = REQUIRED) Instant createdAt,
      @Schema(requiredMode = REQUIRED) Instant updatedAt) {

    static RoleResponse from(RoleView view) {
      var role = view.role();
      return new RoleResponse(
          role.id().value(),
          role.applicationId().value(),
          role.code(),
          role.name(),
          role.description(),
          view.permissions().stream().map(PermissionResponse::from).toList(),
          role.createdAt(),
          role.updatedAt());
    }
  }
}

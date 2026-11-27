package com.henryfll.auth.apps.infrastructure.web;

import com.henryfll.auth.apps.application.port.in.ManagePermissionsUseCase;
import com.henryfll.auth.apps.application.port.in.ManagePermissionsUseCase.CreatePermissionCommand;
import com.henryfll.auth.apps.application.port.in.ManageRolesUseCase;
import com.henryfll.auth.apps.application.port.in.ManageRolesUseCase.CreateRoleCommand;
import com.henryfll.auth.apps.application.port.in.ManageRolesUseCase.RoleView;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Permission;
import com.henryfll.auth.apps.domain.PermissionId;
import com.henryfll.auth.apps.domain.RoleId;
import io.swagger.v3.oas.annotations.Operation;
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
  @Operation(summary = "Listar los permisos de una aplicación")
  List<PermissionResponse> listPermissions(@PathVariable UUID appId) {
    return permissions.listPermissions(new ApplicationId(appId)).stream()
        .map(PermissionResponse::from)
        .toList();
  }

  @PostMapping("/roles")
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
  @Operation(summary = "Listar los roles de una aplicación con sus permisos")
  List<RoleResponse> listRoles(@PathVariable UUID appId) {
    return roles.listRoles(new ApplicationId(appId)).stream().map(RoleResponse::from).toList();
  }

  @GetMapping("/roles/{roleId}")
  @Operation(summary = "Obtener un rol con sus permisos")
  RoleResponse getRole(@PathVariable UUID appId, @PathVariable UUID roleId) {
    return RoleResponse.from(roles.getRole(new ApplicationId(appId), new RoleId(roleId)));
  }

  @PutMapping("/roles/{roleId}/permissions")
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

  record PermissionResponse(UUID id, String code, String description, Instant createdAt) {

    static PermissionResponse from(Permission p) {
      return new PermissionResponse(p.id().value(), p.code(), p.description(), p.createdAt());
    }
  }

  record RoleResponse(
      UUID id,
      UUID applicationId,
      String code,
      String name,
      String description,
      List<PermissionResponse> permissions,
      Instant createdAt,
      Instant updatedAt) {

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

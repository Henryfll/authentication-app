package com.henryfll.auth.apps.infrastructure.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.apps.application.port.in.ManageApplicationsUseCase;
import com.henryfll.auth.apps.application.port.in.ManageApplicationsUseCase.CreateApplicationCommand;
import com.henryfll.auth.apps.application.port.in.ManageApplicationsUseCase.UpdateApplicationCommand;
import com.henryfll.auth.apps.domain.ActivationStatus;
import com.henryfll.auth.apps.domain.Application;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.RoleId;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.web.ErrorResponses;
import com.henryfll.auth.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/applications")
@Tag(name = "Admin · Applications")
class AdminApplicationController {

  private final ManageApplicationsUseCase applications;

  AdminApplicationController(ManageApplicationsUseCase applications) {
    this.applications = applications;
  }

  @PostMapping
  @ApiResponse(
      responseCode = "201",
      description = "Aplicación registrada",
      headers =
          @Header(
              name = "Location",
              description = "URL del recurso creado",
              schema = @Schema(type = "string")))
  @ErrorResponses({ErrorCode.APPLICATION_CODE_ALREADY_EXISTS})
  @Operation(summary = "Registrar una aplicación")
  ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
    Application app =
        applications.create(
            new CreateApplicationCommand(request.code(), request.name(), request.description()));
    return ResponseEntity.created(URI.create("/api/v1/admin/applications/" + app.id()))
        .body(ApplicationResponse.from(app));
  }

  @GetMapping
  @Operation(summary = "Listar aplicaciones (paginado)")
  PageResponse<ApplicationResponse> list(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        applications.list(PageQuery.of(page, size)), ApplicationResponse::from);
  }

  @GetMapping("/{appId}")
  @ErrorResponses({ErrorCode.APPLICATION_NOT_FOUND})
  @Operation(summary = "Obtener una aplicación")
  ApplicationResponse get(@PathVariable UUID appId) {
    return ApplicationResponse.from(applications.get(new ApplicationId(appId)));
  }

  @PatchMapping("/{appId}")
  @ErrorResponses({
    ErrorCode.APPLICATION_NOT_FOUND,
    ErrorCode.ROLE_NOT_FOUND,
    ErrorCode.BUSINESS_RULE_VIOLATION,
    ErrorCode.CONFLICT
  })
  @Operation(
      summary = "Actualizar una aplicación",
      description = "Solo se modifican los campos enviados. El código no se puede cambiar.")
  ApplicationResponse update(
      @PathVariable UUID appId, @Valid @RequestBody UpdateApplicationRequest request) {
    return ApplicationResponse.from(
        applications.update(
            new ApplicationId(appId),
            new UpdateApplicationCommand(
                request.name(),
                request.description(),
                request.status(),
                request.selfRegistration(),
                request.defaultRoleId() == null ? null : new RoleId(request.defaultRoleId()))));
  }

  record CreateApplicationRequest(
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 100) String name,
      @Size(max = 1000) String description) {}

  record UpdateApplicationRequest(
      @Size(min = 1, max = 100) String name,
      @Size(max = 1000) String description,
      ActivationStatus status,
      Boolean selfRegistration,
      UUID defaultRoleId) {}

  record ApplicationResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) String code,
      @Schema(requiredMode = REQUIRED) String name,
      @Schema(requiredMode = REQUIRED, nullable = true) String description,
      @Schema(requiredMode = REQUIRED) ActivationStatus status,
      @Schema(requiredMode = REQUIRED) boolean selfRegistration,
      @Schema(requiredMode = REQUIRED, nullable = true) UUID defaultRoleId,
      @Schema(requiredMode = REQUIRED) Instant createdAt,
      @Schema(requiredMode = REQUIRED) Instant updatedAt) {

    static ApplicationResponse from(Application app) {
      return new ApplicationResponse(
          app.id().value(),
          app.code().value(),
          app.name(),
          app.description(),
          app.status(),
          app.selfRegistration(),
          app.defaultRoleId() == null ? null : app.defaultRoleId().value(),
          app.createdAt(),
          app.updatedAt());
    }
  }
}

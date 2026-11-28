package com.henryfll.auth.access.infrastructure.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.access.application.port.in.ListMyApplicationsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lanzador de aplicaciones: a qué aplicaciones puede entrar el usuario autenticado. */
@RestController
@Tag(name = "Me")
@SecurityRequirement(name = "bearer")
class MeApplicationsController {

  private final ListMyApplicationsUseCase myApplications;

  MeApplicationsController(ListMyApplicationsUseCase myApplications) {
    this.myApplications = myApplications;
  }

  @GetMapping("/api/v1/users/me/applications")
  @Operation(
      operationId = "listMyApplications",
      summary = "Aplicaciones a las que el usuario puede entrar ahora",
      description = "Solo accesos activos, dentro de su vigencia, de aplicaciones activas.")
  List<MyApplicationResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return myApplications.listEffective(UUID.fromString(jwt.getSubject())).stream()
        .map(m ->
            new MyApplicationResponse(
                m.application().id(), m.application().code(), m.application().name(), m.roles()))
        .toList();
  }

  record MyApplicationResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) String code,
      @Schema(requiredMode = REQUIRED) String name,
      @Schema(requiredMode = REQUIRED) List<String> roles) {}
}

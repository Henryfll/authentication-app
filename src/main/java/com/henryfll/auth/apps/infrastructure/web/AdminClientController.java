package com.henryfll.auth.apps.infrastructure.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.henryfll.auth.apps.application.port.in.ManageClientsUseCase;
import com.henryfll.auth.apps.application.port.in.ManageClientsUseCase.ClientWithSecret;
import com.henryfll.auth.apps.application.port.in.ManageClientsUseCase.CreateClientCommand;
import com.henryfll.auth.apps.application.port.in.ManageClientsUseCase.UpdateClientCommand;
import com.henryfll.auth.apps.domain.ActivationStatus;
import com.henryfll.auth.apps.domain.ApplicationClient;
import com.henryfll.auth.apps.domain.ApplicationClientId;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.ClientPlatform;
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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/applications/{appId}/clients")
@Tag(name = "Admin · Application clients")
class AdminClientController {

  private final ManageClientsUseCase clients;

  AdminClientController(ManageClientsUseCase clients) {
    this.clients = clients;
  }

  @PostMapping
  @ApiResponse(
      responseCode = "201",
      description =
          "Cliente registrado. Para clientes SERVICE incluye el secreto, que solo se muestra ahora",
      headers =
          @Header(
              name = "Location",
              description = "URL del recurso creado",
              schema = @Schema(type = "string")))
  @ErrorResponses({
    ErrorCode.APPLICATION_NOT_FOUND,
    ErrorCode.CLIENT_ID_ALREADY_EXISTS,
    ErrorCode.INVALID_CLIENT_CONFIGURATION
  })
  @Operation(
      operationId = "createClient",
      summary = "Registrar un cliente (web, móvil o servicio)",
      description =
          "Para clientes SERVICE la respuesta incluye `clientSecret`. Es la única vez que se"
              + " muestra: solo se guarda su hash.")
  ResponseEntity<ClientResponse> create(
      @PathVariable UUID appId, @Valid @RequestBody CreateClientRequest request) {
    ClientWithSecret created =
        clients.create(
            new ApplicationId(appId),
            new CreateClientCommand(
                request.clientId(),
                request.name(),
                request.platform(),
                request.allowedOrigins(),
                seconds(request.accessTokenTtlSeconds()),
                seconds(request.refreshTokenTtlSeconds())));
    return ResponseEntity.created(
            URI.create("/api/v1/admin/applications/" + appId + "/clients/" + created.client().id()))
        .body(ClientResponse.from(created.client(), created.plainSecret()));
  }

  @GetMapping
  @ErrorResponses({ErrorCode.APPLICATION_NOT_FOUND})
  @Operation(operationId = "listClients", summary = "Listar los clientes de una aplicación")
  List<ClientResponse> list(@PathVariable UUID appId) {
    return clients.list(new ApplicationId(appId)).stream()
        .map(c -> ClientResponse.from(c, null))
        .toList();
  }

  @PatchMapping("/{clientId}")
  @ErrorResponses({
    ErrorCode.APPLICATION_NOT_FOUND,
    ErrorCode.CLIENT_NOT_FOUND,
    ErrorCode.INVALID_CLIENT_CONFIGURATION,
    ErrorCode.CONFLICT
  })
  @Operation(
      operationId = "updateClient",
      summary = "Actualizar un cliente",
      description = "Solo se modifican los campos enviados. `client_id` y plataforma son fijos.")
  ClientResponse update(
      @PathVariable UUID appId,
      @PathVariable UUID clientId,
      @Valid @RequestBody UpdateClientRequest request) {
    return ClientResponse.from(
        clients.update(
            new ApplicationId(appId),
            new ApplicationClientId(clientId),
            new UpdateClientCommand(
                request.name(),
                request.allowedOrigins(),
                seconds(request.accessTokenTtlSeconds()),
                seconds(request.refreshTokenTtlSeconds()),
                request.status())),
        null);
  }

  @PostMapping("/{clientId}/secret")
  @ErrorResponses({
    ErrorCode.APPLICATION_NOT_FOUND,
    ErrorCode.CLIENT_NOT_FOUND,
    ErrorCode.INVALID_CLIENT_CONFIGURATION
  })
  @Operation(
      operationId = "rotateClientSecret",
      summary = "Regenerar el secreto de un cliente SERVICE",
      description = "El secreto anterior deja de ser válido inmediatamente.")
  ClientResponse rotateSecret(@PathVariable UUID appId, @PathVariable UUID clientId) {
    ClientWithSecret rotated =
        clients.rotateSecret(new ApplicationId(appId), new ApplicationClientId(clientId));
    return ClientResponse.from(rotated.client(), rotated.plainSecret());
  }

  private static Duration seconds(Integer value) {
    return value == null ? null : Duration.ofSeconds(value);
  }

  record CreateClientRequest(
      @Size(max = 100) String clientId,
      @NotBlank @Size(max = 100) String name,
      @NotNull ClientPlatform platform,
      @Size(max = 20) List<@NotBlank String> allowedOrigins,
      @Positive Integer accessTokenTtlSeconds,
      @Positive Integer refreshTokenTtlSeconds) {}

  record UpdateClientRequest(
      @Size(min = 1, max = 100) String name,
      @Size(max = 20) List<@NotBlank String> allowedOrigins,
      @Positive Integer accessTokenTtlSeconds,
      @Positive Integer refreshTokenTtlSeconds,
      ActivationStatus status) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ClientResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) UUID applicationId,
      @Schema(requiredMode = REQUIRED) String clientId,
      @Schema(requiredMode = REQUIRED) String name,
      @Schema(requiredMode = REQUIRED) ClientPlatform platform,
      @Schema(requiredMode = REQUIRED) boolean confidential,
      @Schema(requiredMode = REQUIRED) List<String> allowedOrigins,
      @Schema(requiredMode = REQUIRED) long accessTokenTtlSeconds,
      @Schema(requiredMode = REQUIRED) long refreshTokenTtlSeconds,
      @Schema(requiredMode = REQUIRED) ActivationStatus status,
      @Schema(requiredMode = REQUIRED) Instant createdAt,
      @Schema(requiredMode = REQUIRED) Instant updatedAt,
      @Schema(requiredMode = NOT_REQUIRED) String clientSecret) {

    static ClientResponse from(ApplicationClient c, String plainSecret) {
      return new ClientResponse(
          c.id().value(),
          c.applicationId().value(),
          c.clientId(),
          c.name(),
          c.platform(),
          c.isConfidential(),
          c.allowedOrigins(),
          c.accessTokenTtl().toSeconds(),
          c.refreshTokenTtl().toSeconds(),
          c.status(),
          c.createdAt(),
          c.updatedAt(),
          plainSecret);
    }

    @Override
    public String toString() {
      return "ClientResponse[clientId=" + clientId + ", clientSecret=***]";
    }
  }
}

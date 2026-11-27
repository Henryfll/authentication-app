package com.henryfll.auth.auth.infrastructure.web;

import com.henryfll.auth.auth.application.port.in.LoginUseCase;
import com.henryfll.auth.auth.application.port.in.LoginUseCase.LoginCommand;
import com.henryfll.auth.auth.application.port.in.LoginUseCase.LoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

  static final String CLIENT_ID_HEADER = "X-Client-Id";

  private final LoginUseCase login;

  AuthController(LoginUseCase login) {
    this.login = login;
  }

  @PostMapping("/login")
  @Operation(
      summary = "Login con email y contraseña",
      description =
          "Emite un access token (JWT RS256) para la aplicación del cliente indicado en"
              + " X-Client-Id, con los roles y permisos del usuario en esa aplicación.")
  ResponseEntity<LoginResponse> login(
      @Parameter(description = "client_id público del cliente (web o móvil)")
          @RequestHeader(CLIENT_ID_HEADER)
          String clientId,
      @Valid @RequestBody LoginRequest request) {
    LoginResult result =
        login.login(new LoginCommand(clientId, request.email(), request.password()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
        .body(
            new LoginResponse(
                result.accessToken(),
                "Bearer",
                result.expiresInSeconds(),
                result.expiresAt(),
                result.applicationCode()));
  }

  record LoginRequest(
      @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {

    @Override
    public String toString() {
      return "LoginRequest[email=" + email + ", password=***]";
    }
  }

  record LoginResponse(
      String accessToken, String tokenType, long expiresIn, Instant expiresAt, String application) {

    @Override
    public String toString() {
      return "LoginResponse[application=" + application + ", accessToken=***]";
    }
  }
}

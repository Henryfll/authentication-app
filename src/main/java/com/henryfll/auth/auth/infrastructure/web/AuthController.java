package com.henryfll.auth.auth.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.henryfll.auth.auth.application.port.in.AuthTokens;
import com.henryfll.auth.auth.application.port.in.LoginUseCase;
import com.henryfll.auth.auth.application.port.in.LoginUseCase.LoginCommand;
import com.henryfll.auth.auth.application.port.in.ManageSessionsUseCase;
import com.henryfll.auth.auth.application.port.in.RefreshUseCase;
import com.henryfll.auth.auth.application.port.in.RefreshUseCase.RefreshCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, refresh y logout. El refresh token viaja en una cookie HttpOnly para clientes web y en el
 * body para clientes móviles; lo decide la plataforma del cliente registrado (§6.3), no el
 * llamante.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

  static final String CLIENT_ID_HEADER = "X-Client-Id";

  private final LoginUseCase login;
  private final RefreshUseCase refresh;
  private final ManageSessionsUseCase sessions;
  private final RefreshCookies cookies;

  AuthController(
      LoginUseCase login,
      RefreshUseCase refresh,
      ManageSessionsUseCase sessions,
      RefreshCookies cookies) {
    this.login = login;
    this.refresh = refresh;
    this.sessions = sessions;
    this.cookies = cookies;
  }

  @PostMapping("/login")
  @Operation(
      summary = "Login con email y contraseña",
      description =
          "Abre una sesión y emite un access token (JWT RS256) para la aplicación del cliente"
              + " indicado en X-Client-Id, más un refresh token: en cookie HttpOnly para clientes"
              + " WEB y en el campo `refreshToken` para clientes MOBILE.")
  ResponseEntity<TokenResponse> login(
      @Parameter(description = "client_id público del cliente (web o móvil)")
          @RequestHeader(CLIENT_ID_HEADER)
          String clientId,
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request) {
    AuthTokens tokens =
        login.login(
            new LoginCommand(
                clientId,
                body.email(),
                body.password(),
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT),
                body.deviceName()));
    return respond(tokens);
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Renovar los tokens",
      description =
          "Los clientes WEB envían la cookie del refresh token; los MOBILE, el body"
              + " `{\"refreshToken\": \"...\"}`. Cada refresh token se puede usar una sola vez:"
              + " reutilizarlo revoca la sesión completa.")
  ResponseEntity<TokenResponse> refresh(
      @RequestHeader(CLIENT_ID_HEADER) String clientId,
      @CookieValue(name = "${app.security.refresh-cookie.name:refresh_token}", required = false)
          String cookieToken,
      @RequestBody(required = false) RefreshRequest body,
      HttpServletRequest request) {
    boolean fromCookie = cookieToken != null && !cookieToken.isBlank();
    String token = fromCookie ? cookieToken : (body == null ? null : body.refreshToken());
    AuthTokens tokens =
        refresh.refresh(
            new RefreshCommand(
                clientId,
                token,
                fromCookie,
                request.getHeader(HttpHeaders.ORIGIN),
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT)));
    return respond(tokens);
  }

  @PostMapping("/logout")
  @Operation(summary = "Cerrar la sesión actual (la del access token)")
  ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
    String sid = jwt.getClaimAsString("sid");
    if (sid != null) {
      sessions.logout(UUID.fromString(jwt.getSubject()), UUID.fromString(sid));
    }
    return noContentClearingCookie();
  }

  @PostMapping("/logout-all")
  @Operation(summary = "Cerrar todas las sesiones del usuario en todos sus dispositivos")
  ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
    sessions.logoutAll(UUID.fromString(jwt.getSubject()));
    return noContentClearingCookie();
  }

  private ResponseEntity<TokenResponse> respond(AuthTokens tokens) {
    var response =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache");
    String bodyRefreshToken = tokens.refreshToken();
    if (tokens.usesRefreshCookie()) {
      response.header(HttpHeaders.SET_COOKIE, cookies.issue(tokens).toString());
      bodyRefreshToken = null;
    }
    return response.body(
        new TokenResponse(
            tokens.accessToken(),
            "Bearer",
            tokens.expiresInSeconds(),
            tokens.accessTokenExpiresAt(),
            bodyRefreshToken,
            tokens.refreshTokenExpiresAt(),
            tokens.applicationCode(),
            tokens.sessionId()));
  }

  private ResponseEntity<Void> noContentClearingCookie() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }

  record LoginRequest(
      @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(max = 128) String password,
      @Size(max = 100) String deviceName) {

    @Override
    public String toString() {
      return "LoginRequest[email=" + email + ", password=***]";
    }
  }

  record RefreshRequest(@Size(max = 200) String refreshToken) {

    @Override
    public String toString() {
      return "RefreshRequest[refreshToken=***]";
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record TokenResponse(
      String accessToken,
      String tokenType,
      long expiresIn,
      Instant expiresAt,
      String refreshToken,
      Instant refreshTokenExpiresAt,
      String application,
      UUID sessionId) {

    @Override
    public String toString() {
      return "TokenResponse[application=" + application + ", accessToken=***, refreshToken=***]";
    }
  }
}

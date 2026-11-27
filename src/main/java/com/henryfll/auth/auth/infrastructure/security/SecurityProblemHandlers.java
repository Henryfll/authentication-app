package com.henryfll.auth.auth.infrastructure.security;

import com.henryfll.auth.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuestas 401 y 403 de la capa de seguridad en el mismo formato Problem Details (RFC 9457) que
 * el resto de la API. Se generan en un filtro, antes de llegar a los controllers.
 */
final class SecurityProblemHandlers {

  private SecurityProblemHandlers() {}

  static AuthenticationEntryPoint entryPoint() {
    return (request, response, exception) -> {
      boolean invalidToken = isInvalidToken(exception);
      response.setHeader(
          HttpHeaders.WWW_AUTHENTICATE, invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
      write(
          request,
          response,
          invalidToken ? ErrorCode.INVALID_TOKEN : ErrorCode.AUTHENTICATION_REQUIRED,
          invalidToken
              ? "The access token is invalid or has expired"
              : "A valid bearer access token is required");
    };
  }

  static AccessDeniedHandler accessDeniedHandler() {
    return (HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException exception) ->
        write(
            request,
            response,
            ErrorCode.INSUFFICIENT_PERMISSIONS,
            "The access token does not grant permission for this operation");
  }

  private static boolean isInvalidToken(AuthenticationException exception) {
    return exception instanceof InvalidBearerTokenException
        || exception.getCause() instanceof InvalidBearerTokenException;
  }

  private static void write(
      HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
      throws IOException {
    int status = code.type().httpStatus();
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    String body =
        "{\"type\":\"about:blank\""
            + ",\"title\":"
            + json(code.title())
            + ",\"status\":"
            + status
            + ",\"detail\":"
            + json(detail)
            + ",\"instance\":"
            + json(request.getRequestURI())
            + ",\"code\":"
            + json(code.name())
            + ",\"timestamp\":"
            + json(Instant.now().toString())
            + "}";
    response.getWriter().write(body);
  }

  private static String json(String value) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}

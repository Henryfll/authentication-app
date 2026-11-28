package com.henryfll.auth.user.infrastructure.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.web.ErrorResponses;
import com.henryfll.auth.shared.web.PageResponse;
import com.henryfll.auth.user.application.port.in.ChangePasswordUseCase;
import com.henryfll.auth.user.application.port.in.ChangeUserStatusUseCase;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase.CreateUserCommand;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase.UserSearchCriteria;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin · Users")
class AdminUserController {

  private final CreateUserUseCase createUser;
  private final FindUsersUseCase findUsers;
  private final ChangeUserStatusUseCase changeUserStatus;
  private final ChangePasswordUseCase passwords;

  AdminUserController(
      CreateUserUseCase createUser,
      FindUsersUseCase findUsers,
      ChangeUserStatusUseCase changeUserStatus,
      ChangePasswordUseCase passwords) {
    this.createUser = createUser;
    this.findUsers = findUsers;
    this.changeUserStatus = changeUserStatus;
    this.passwords = passwords;
  }

  @PostMapping
  @ApiResponse(
      responseCode = "201",
      description = "Usuario creado",
      headers =
          @Header(
              name = "Location",
              description = "URL del recurso creado",
              schema = @Schema(type = "string")))
  @ErrorResponses({ErrorCode.USER_EMAIL_ALREADY_EXISTS, ErrorCode.PASSWORD_POLICY_VIOLATION})
  @Operation(operationId = "createUser", summary = "Crear un usuario")
  ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    User user =
        createUser.create(
            new CreateUserCommand(
                request.email(), request.password(), request.firstName(), request.lastName()));
    return ResponseEntity.created(URI.create("/api/v1/admin/users/" + user.id()))
        .body(UserResponse.from(user));
  }

  @GetMapping
  @Operation(operationId = "listUsers", summary = "Listar usuarios (paginado)")
  PageResponse<UserResponse> list(
      @RequestParam(required = false) String email,
      @RequestParam(required = false) UserStatus status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        findUsers.search(new UserSearchCriteria(email, status), PageQuery.of(page, size)),
        UserResponse::from);
  }

  @GetMapping("/{userId}")
  @ErrorResponses({ErrorCode.USER_NOT_FOUND})
  @Operation(operationId = "getUser", summary = "Obtener un usuario")
  UserResponse get(@PathVariable UUID userId) {
    return UserResponse.from(findUsers.get(new UserId(userId)));
  }

  @PatchMapping("/{userId}/status")
  @ErrorResponses({ErrorCode.USER_NOT_FOUND, ErrorCode.CONFLICT})
  @Operation(operationId = "changeUserStatus", summary = "Cambiar el estado de un usuario")
  UserResponse changeStatus(
      @PathVariable UUID userId, @Valid @RequestBody ChangeUserStatusRequest request) {
    return UserResponse.from(changeUserStatus.changeStatus(new UserId(userId), request.status()));
  }

  @PutMapping("/{userId}/password")
  @ApiResponse(
      responseCode = "204",
      description = "Contraseña restablecida. Se cierran todas las sesiones del usuario")
  @ErrorResponses({ErrorCode.USER_NOT_FOUND, ErrorCode.PASSWORD_POLICY_VIOLATION})
  @Operation(
      operationId = "resetUserPassword",
      summary = "Restablecer la contraseña de un usuario",
      description =
          "Sustituye la recuperación por email mientras no exista. Levanta el bloqueo temporal"
              + " por intentos fallidos y cierra todas las sesiones del usuario.")
  ResponseEntity<Void> resetPassword(
      @PathVariable UUID userId, @Valid @RequestBody ResetPasswordRequest request) {
    passwords.resetPassword(new UserId(userId), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  record ResetPasswordRequest(@NotBlank @Size(max = 128) String newPassword) {

    @Override
    public String toString() {
      return "ResetPasswordRequest[***]";
    }
  }

  record CreateUserRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 128) String password,
      @Size(max = 100) String firstName,
      @Size(max = 100) String lastName) {

    @Override
    public String toString() {
      return "CreateUserRequest[email=" + email + ", password=***]";
    }
  }

  record ChangeUserStatusRequest(@NotNull UserStatus status) {}

  record UserResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) String email,
      @Schema(requiredMode = REQUIRED, nullable = true) String firstName,
      @Schema(requiredMode = REQUIRED, nullable = true) String lastName,
      @Schema(requiredMode = REQUIRED) UserStatus status,
      @Schema(requiredMode = REQUIRED) boolean emailVerified,
      @Schema(requiredMode = REQUIRED) Instant createdAt,
      @Schema(requiredMode = REQUIRED) Instant updatedAt) {

    static UserResponse from(User user) {
      return new UserResponse(
          user.id().value(),
          user.email().value(),
          user.firstName(),
          user.lastName(),
          user.status(),
          user.isEmailVerified(),
          user.createdAt(),
          user.updatedAt());
    }
  }
}

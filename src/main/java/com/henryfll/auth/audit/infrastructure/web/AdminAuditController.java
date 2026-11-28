package com.henryfll.auth.audit.infrastructure.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.audit.application.port.in.QueryAuditEventsUseCase;
import com.henryfll.auth.audit.application.port.in.QueryAuditEventsUseCase.AuditQuery;
import com.henryfll.auth.audit.domain.AuditEntry;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@Tag(name = "Admin · Audit")
class AdminAuditController {

  private final QueryAuditEventsUseCase audit;

  AdminAuditController(QueryAuditEventsUseCase audit) {
    this.audit = audit;
  }

  @GetMapping
  @Operation(
      summary = "Consultar la auditoría (paginado, del más reciente al más antiguo)",
      description =
          "`userId` filtra los eventos en los que el usuario es actor o destinatario. `from` es"
              + " inclusivo y `to` exclusivo (ISO-8601).")
  PageResponse<AuditEventResponse> search(
      @RequestParam(required = false) AuditEventType type,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) UUID applicationId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        audit.search(
            new AuditQuery(type, userId, applicationId, from, to), PageQuery.of(page, size)),
        AuditEventResponse::from);
  }

  record AuditEventResponse(
      @Schema(requiredMode = REQUIRED) UUID id,
      @Schema(requiredMode = REQUIRED) AuditEventType type,
      @Schema(requiredMode = REQUIRED, nullable = true) UUID actorUserId,
      @Schema(requiredMode = REQUIRED, nullable = true) UUID targetUserId,
      @Schema(requiredMode = REQUIRED, nullable = true) UUID applicationId,
      @Schema(requiredMode = REQUIRED, nullable = true) String ipAddress,
      @Schema(requiredMode = REQUIRED) Map<String, Object> details,
      @Schema(requiredMode = REQUIRED) Instant createdAt) {

    static AuditEventResponse from(AuditEntry e) {
      return new AuditEventResponse(
          e.id(),
          e.type(),
          e.actorUserId(),
          e.targetUserId(),
          e.applicationId(),
          e.ipAddress(),
          e.details(),
          e.createdAt());
    }
  }
}

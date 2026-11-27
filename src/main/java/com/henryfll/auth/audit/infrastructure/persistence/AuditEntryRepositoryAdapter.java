package com.henryfll.auth.audit.infrastructure.persistence;

import com.henryfll.auth.audit.application.port.in.QueryAuditEventsUseCase.AuditQuery;
import com.henryfll.auth.audit.application.port.out.AuditEntryRepository;
import com.henryfll.auth.audit.domain.AuditEntry;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class AuditEntryRepositoryAdapter implements AuditEntryRepository {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final AuditEntryJpaRepository jpa;
  private final JsonMapper json;

  AuditEntryRepositoryAdapter(AuditEntryJpaRepository jpa, JsonMapper json) {
    this.jpa = jpa;
    this.json = json;
  }

  @Override
  public void save(AuditEntry entry) {
    AuditEntryJpaEntity e = new AuditEntryJpaEntity(entry.id());
    e.type = entry.type().name();
    e.actorUserId = entry.actorUserId();
    e.targetUserId = entry.targetUserId();
    e.applicationId = entry.applicationId();
    e.ipAddress = entry.ipAddress();
    e.details = entry.details().isEmpty() ? null : json.writeValueAsString(entry.details());
    e.createdAt = entry.createdAt();
    jpa.save(e);
  }

  @Override
  public PageResult<AuditEntry> search(AuditQuery query, PageQuery page) {
    Page<AuditEntryJpaEntity> result =
        jpa.findAll(
            specification(query),
            PageRequest.of(page.page(), page.size(), Sort.by("createdAt").descending()));
    return new PageResult<>(
        result.getContent().stream().map(this::toDomain).toList(),
        page.page(),
        page.size(),
        result.getTotalElements());
  }

  private static Specification<AuditEntryJpaEntity> specification(AuditQuery q) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (q.type() != null) {
        predicates.add(cb.equal(root.get("type"), q.type().name()));
      }
      if (q.userId() != null) {
        predicates.add(
            cb.or(
                cb.equal(root.get("targetUserId"), q.userId()),
                cb.equal(root.get("actorUserId"), q.userId())));
      }
      if (q.applicationId() != null) {
        predicates.add(cb.equal(root.get("applicationId"), q.applicationId()));
      }
      if (q.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.from()));
      }
      if (q.to() != null) {
        predicates.add(cb.lessThan(root.get("createdAt"), q.to()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private AuditEntry toDomain(AuditEntryJpaEntity e) {
    return new AuditEntry(
        e.id,
        AuditEventType.valueOf(e.type),
        e.actorUserId,
        e.targetUserId,
        e.applicationId,
        e.ipAddress,
        e.details == null ? Map.of() : json.readValue(e.details, MAP),
        e.createdAt);
  }
}

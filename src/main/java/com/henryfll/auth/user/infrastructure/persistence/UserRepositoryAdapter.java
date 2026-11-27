package com.henryfll.auth.user.infrastructure.persistence;

import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase.UserSearchCriteria;
import com.henryfll.auth.user.application.port.out.UserRepository;
import com.henryfll.auth.user.domain.Email;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;
import com.henryfll.auth.user.infrastructure.persistence.UserJpaEntity.UserStatusColumn;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class UserRepositoryAdapter implements UserRepository {

  private final UserJpaRepository jpa;

  UserRepositoryAdapter(UserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public User save(User user) {
    UserJpaEntity entity =
        jpa.findById(user.id().value()).orElseGet(() -> new UserJpaEntity(user.id().value()));
    copy(user, entity);
    return toDomain(jpa.saveAndFlush(entity));
  }

  @Override
  public Optional<User> findById(UserId id) {
    return jpa.findById(id.value()).map(UserRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return jpa.findByEmail(email.value()).map(UserRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByEmail(Email email) {
    return jpa.existsByEmail(email.value());
  }

  @Override
  public PageResult<User> search(UserSearchCriteria criteria, PageQuery page) {
    Page<UserJpaEntity> result =
        jpa.findAll(
            specification(criteria),
            PageRequest.of(page.page(), page.size(), Sort.by("createdAt").descending()));
    return new PageResult<>(
        result.getContent().stream().map(UserRepositoryAdapter::toDomain).toList(),
        page.page(),
        page.size(),
        result.getTotalElements());
  }

  private static Specification<UserJpaEntity> specification(UserSearchCriteria criteria) {
    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      if (criteria.emailContains() != null && !criteria.emailContains().isBlank()) {
        String pattern =
            "%" + escapeLike(criteria.emailContains().strip().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(cb.like(cb.lower(root.get("email")), pattern, '\\'));
      }
      if (criteria.status() != null) {
        predicates.add(
            cb.equal(root.get("status"), UserStatusColumn.valueOf(criteria.status().name())));
      }
      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static void copy(User user, UserJpaEntity entity) {
    entity.setEmail(user.email().value());
    entity.setPasswordHash(user.passwordHash());
    entity.setFirstName(user.firstName());
    entity.setLastName(user.lastName());
    entity.setStatus(UserStatusColumn.valueOf(user.status().name()));
    entity.setEmailVerifiedAt(user.emailVerifiedAt());
    entity.setFailedLoginAttempts(user.failedLoginAttempts());
    entity.setLockedUntil(user.lockedUntil());
    entity.setPasswordChangedAt(user.passwordChangedAt());
    entity.setCreatedAt(user.createdAt());
    entity.setUpdatedAt(user.updatedAt());
  }

  private static User toDomain(UserJpaEntity e) {
    return User.restore(
        new UserId(e.getId()),
        new Email(e.getEmail()),
        e.getPasswordHash(),
        e.getFirstName(),
        e.getLastName(),
        UserStatus.valueOf(e.getStatus().name()),
        e.getEmailVerifiedAt(),
        e.getFailedLoginAttempts(),
        e.getLockedUntil(),
        e.getPasswordChangedAt(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}

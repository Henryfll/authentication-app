package com.henryfll.auth.access.application.port.out;

import com.henryfll.auth.access.domain.AccessGrant;
import com.henryfll.auth.access.domain.AccessStatus;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessGrantRepository {

  AccessGrant save(AccessGrant grant);

  Optional<AccessGrant> find(UUID userId, UUID applicationId);

  PageResult<AccessGrant> findByApplication(
      UUID applicationId, AccessStatus status, PageQuery page);

  List<AccessGrant> findByUser(UUID userId);
}

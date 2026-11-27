package com.henryfll.auth.audit.application.port.out;

import com.henryfll.auth.audit.application.port.in.QueryAuditEventsUseCase.AuditQuery;
import com.henryfll.auth.audit.domain.AuditEntry;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;

public interface AuditEntryRepository {

  void save(AuditEntry entry);

  PageResult<AuditEntry> search(AuditQuery query, PageQuery page);
}

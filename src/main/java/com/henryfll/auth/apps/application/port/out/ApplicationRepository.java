package com.henryfll.auth.apps.application.port.out;

import com.henryfll.auth.apps.domain.Application;
import com.henryfll.auth.apps.domain.ApplicationCode;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.util.Optional;

public interface ApplicationRepository {

  Application save(Application application);

  Optional<Application> findById(ApplicationId id);

  boolean existsByCode(ApplicationCode code);

  PageResult<Application> findAll(PageQuery page);
}

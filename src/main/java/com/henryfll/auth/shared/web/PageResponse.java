package com.henryfll.auth.shared.web;

import com.henryfll.auth.shared.pagination.PageResult;
import java.util.List;
import java.util.function.Function;

/** Representación JSON de una página. */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
    PageResult<T> mapped = result.map(mapper);
    return new PageResponse<>(
        mapped.content(),
        mapped.page(),
        mapped.size(),
        mapped.totalElements(),
        mapped.totalPages());
  }
}

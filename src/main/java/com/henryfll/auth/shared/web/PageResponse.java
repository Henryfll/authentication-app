package com.henryfll.auth.shared.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.henryfll.auth.shared.pagination.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

/** Representación JSON de una página. */
public record PageResponse<T>(
    @Schema(requiredMode = REQUIRED) List<T> content,
    @Schema(requiredMode = REQUIRED) int page,
    @Schema(requiredMode = REQUIRED) int size,
    @Schema(requiredMode = REQUIRED) long totalElements,
    @Schema(requiredMode = REQUIRED) int totalPages) {

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

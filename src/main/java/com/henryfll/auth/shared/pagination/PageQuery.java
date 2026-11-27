package com.henryfll.auth.shared.pagination;

import com.henryfll.auth.shared.error.ValidationException;

/** Petición de una página, independiente de Spring Data. {@code page} empieza en 0. */
public record PageQuery(int page, int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public PageQuery {
    if (page < 0) {
      throw new ValidationException("page", "must be greater than or equal to 0");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new ValidationException("size", "must be between 1 and " + MAX_SIZE);
    }
  }

  public static PageQuery of(Integer page, Integer size) {
    return new PageQuery(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
  }
}

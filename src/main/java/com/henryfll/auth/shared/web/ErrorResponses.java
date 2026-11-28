package com.henryfll.auth.shared.web;

import com.henryfll.auth.shared.error.ErrorCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Códigos de error que una operación puede devolver además de los comunes, para documentarlos en el
 * contrato OpenAPI. El estado HTTP de cada uno se toma de {@link ErrorCode}.
 *
 * <p>Los comunes se añaden solos (ver {@code OpenApiProblemsConfig}): {@code 401} si la operación
 * está protegida, {@code 403} en la administración, {@code 400} si tiene parámetros o cuerpo y
 * {@code 500}. Aquí solo se indican los específicos de la operación.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ErrorResponses {

  ErrorCode[] value();
}

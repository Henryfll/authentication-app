package com.henryfll.auth.apps.domain;

import com.henryfll.auth.shared.error.BusinessRuleException;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.ValidationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Forma concreta de consumir una aplicación (web, móvil o servicio), con su propio {@code
 * client_id} y su configuración de tokens.
 */
public final class ApplicationClient {

  public static final int MAX_NAME_LENGTH = 100;
  public static final int MAX_ORIGINS = 20;
  public static final Duration MIN_ACCESS_TOKEN_TTL = Duration.ofMinutes(1);
  public static final Duration MAX_ACCESS_TOKEN_TTL = Duration.ofHours(1);
  public static final Duration MIN_REFRESH_TOKEN_TTL = Duration.ofHours(1);
  public static final Duration MAX_REFRESH_TOKEN_TTL = Duration.ofDays(90);
  public static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
  public static final Duration DEFAULT_WEB_REFRESH_TOKEN_TTL = Duration.ofDays(7);
  public static final Duration DEFAULT_MOBILE_REFRESH_TOKEN_TTL = Duration.ofDays(30);

  private static final Pattern CLIENT_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{2,99}$");

  private final ApplicationClientId id;
  private final ApplicationId applicationId;
  private final String clientId;
  private String name;
  private final ClientPlatform platform;
  private String secretHash;
  private List<String> allowedOrigins;
  private Duration accessTokenTtl;
  private Duration refreshTokenTtl;
  private ActivationStatus status;
  private final Instant createdAt;
  private Instant updatedAt;

  private ApplicationClient(
      ApplicationClientId id,
      ApplicationId applicationId,
      String clientId,
      String name,
      ClientPlatform platform,
      String secretHash,
      List<String> allowedOrigins,
      Duration accessTokenTtl,
      Duration refreshTokenTtl,
      ActivationStatus status,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.applicationId = Objects.requireNonNull(applicationId, "applicationId");
    this.clientId =
        DomainText.matching(
            "clientId",
            clientId,
            CLIENT_ID,
            "must be 3-100 characters: lowercase letters, digits and hyphens");
    this.name = DomainText.required("name", name, MAX_NAME_LENGTH);
    this.platform = Objects.requireNonNull(platform, "platform");
    if (platform.isConfidential() != (secretHash != null)) {
      throw new BusinessRuleException(
          ErrorCode.INVALID_CLIENT_CONFIGURATION,
          "Only SERVICE clients are confidential and have a secret");
    }
    this.secretHash = secretHash;
    this.allowedOrigins = normalizeOrigins(platform, allowedOrigins);
    this.accessTokenTtl =
        checkTtl("accessTokenTtl", accessTokenTtl, MIN_ACCESS_TOKEN_TTL, MAX_ACCESS_TOKEN_TTL);
    this.refreshTokenTtl =
        checkTtl("refreshTokenTtl", refreshTokenTtl, MIN_REFRESH_TOKEN_TTL, MAX_REFRESH_TOKEN_TTL);
    this.status = Objects.requireNonNull(status, "status");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /**
   * @param secretHash hash del secreto para clientes {@code SERVICE}; {@code null} para el resto.
   * @param accessTokenTtl {@code null} para usar el valor por defecto.
   * @param refreshTokenTtl {@code null} para usar el valor por defecto de la plataforma.
   */
  public static ApplicationClient create(
      ApplicationId applicationId,
      String clientId,
      String name,
      ClientPlatform platform,
      String secretHash,
      List<String> allowedOrigins,
      Duration accessTokenTtl,
      Duration refreshTokenTtl,
      Instant now) {
    Objects.requireNonNull(platform, "platform");
    return new ApplicationClient(
        ApplicationClientId.newId(),
        applicationId,
        clientId,
        name,
        platform,
        secretHash,
        allowedOrigins,
        accessTokenTtl != null ? accessTokenTtl : DEFAULT_ACCESS_TOKEN_TTL,
        refreshTokenTtl != null ? refreshTokenTtl : defaultRefreshTtl(platform),
        ActivationStatus.ACTIVE,
        now,
        now);
  }

  public static ApplicationClient restore(
      ApplicationClientId id,
      ApplicationId applicationId,
      String clientId,
      String name,
      ClientPlatform platform,
      String secretHash,
      List<String> allowedOrigins,
      Duration accessTokenTtl,
      Duration refreshTokenTtl,
      ActivationStatus status,
      Instant createdAt,
      Instant updatedAt) {
    return new ApplicationClient(
        id,
        applicationId,
        clientId,
        name,
        platform,
        secretHash,
        allowedOrigins,
        accessTokenTtl,
        refreshTokenTtl,
        status,
        createdAt,
        updatedAt);
  }

  public void rename(String newName, Instant now) {
    this.name = DomainText.required("name", newName, MAX_NAME_LENGTH);
    this.updatedAt = now;
  }

  public void changeAllowedOrigins(List<String> origins, Instant now) {
    this.allowedOrigins = normalizeOrigins(platform, origins);
    this.updatedAt = now;
  }

  public void changeTokenTtls(Duration accessTtl, Duration refreshTtl, Instant now) {
    if (accessTtl != null) {
      this.accessTokenTtl =
          checkTtl("accessTokenTtl", accessTtl, MIN_ACCESS_TOKEN_TTL, MAX_ACCESS_TOKEN_TTL);
    }
    if (refreshTtl != null) {
      this.refreshTokenTtl =
          checkTtl("refreshTokenTtl", refreshTtl, MIN_REFRESH_TOKEN_TTL, MAX_REFRESH_TOKEN_TTL);
    }
    this.updatedAt = now;
  }

  public void changeStatus(ActivationStatus newStatus, Instant now) {
    this.status = Objects.requireNonNull(newStatus, "status");
    this.updatedAt = now;
  }

  public void rotateSecret(String newSecretHash, Instant now) {
    if (!isConfidential()) {
      throw new BusinessRuleException(
          ErrorCode.INVALID_CLIENT_CONFIGURATION, "Only SERVICE clients have a secret");
    }
    this.secretHash = Objects.requireNonNull(newSecretHash, "secretHash");
    this.updatedAt = now;
  }

  public boolean isConfidential() {
    return platform.isConfidential();
  }

  public boolean isActive() {
    return status == ActivationStatus.ACTIVE;
  }

  private static Duration defaultRefreshTtl(ClientPlatform platform) {
    return platform == ClientPlatform.MOBILE
        ? DEFAULT_MOBILE_REFRESH_TOKEN_TTL
        : DEFAULT_WEB_REFRESH_TOKEN_TTL;
  }

  private static Duration checkTtl(String field, Duration ttl, Duration min, Duration max) {
    Objects.requireNonNull(ttl, field);
    if (ttl.compareTo(min) < 0 || ttl.compareTo(max) > 0) {
      throw new ValidationException(
          field, "must be between " + min.toSeconds() + " and " + max.toSeconds() + " seconds");
    }
    return ttl;
  }

  /**
   * Solo los clientes {@code WEB} tienen orígenes (CORS). Cada origen es {@code
   * scheme://host[:port]}, sin ruta; se normaliza a minúsculas y sin duplicados.
   */
  private static List<String> normalizeOrigins(ClientPlatform platform, List<String> origins) {
    if (origins == null || origins.isEmpty()) {
      return List.of();
    }
    if (platform != ClientPlatform.WEB) {
      throw new BusinessRuleException(
          ErrorCode.INVALID_CLIENT_CONFIGURATION, "Only WEB clients can have allowed origins");
    }
    if (origins.size() > MAX_ORIGINS) {
      throw new ValidationException(
          "allowedOrigins", "must contain at most " + MAX_ORIGINS + " origins");
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String origin : origins) {
      normalized.add(normalizeOrigin(origin));
    }
    return List.copyOf(normalized);
  }

  private static String normalizeOrigin(String origin) {
    String invalid =
        "must be origins like https://app.example.com (scheme, host and optional port)";
    if (origin == null || origin.isBlank()) {
      throw new ValidationException("allowedOrigins", invalid);
    }
    try {
      URI uri = new URI(origin.strip());
      String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
      boolean hasPath = uri.getRawPath() != null && !uri.getRawPath().isEmpty();
      if (!("https".equals(scheme) || "http".equals(scheme))
          || uri.getHost() == null
          || hasPath
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || uri.getRawUserInfo() != null) {
        throw new ValidationException("allowedOrigins", invalid);
      }
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      return scheme + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    } catch (URISyntaxException e) {
      throw new ValidationException("allowedOrigins", invalid);
    }
  }

  public ApplicationClientId id() {
    return id;
  }

  public ApplicationId applicationId() {
    return applicationId;
  }

  public String clientId() {
    return clientId;
  }

  public String name() {
    return name;
  }

  public ClientPlatform platform() {
    return platform;
  }

  public String secretHash() {
    return secretHash;
  }

  public List<String> allowedOrigins() {
    return allowedOrigins;
  }

  public Duration accessTokenTtl() {
    return accessTokenTtl;
  }

  public Duration refreshTokenTtl() {
    return refreshTokenTtl;
  }

  public ActivationStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}

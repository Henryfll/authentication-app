package com.henryfll.auth.auth.application.service;

import com.henryfll.auth.auth.application.port.in.AuthTokens;
import com.henryfll.auth.auth.application.port.out.RefreshTokenRepository;
import com.henryfll.auth.auth.application.port.out.TokenIssuer;
import com.henryfll.auth.auth.domain.AccessTokenClaims;
import com.henryfll.auth.auth.domain.Authorization;
import com.henryfll.auth.auth.domain.LoginClient;
import com.henryfll.auth.auth.domain.RefreshToken;
import com.henryfll.auth.auth.domain.RefreshTokenValues;
import com.henryfll.auth.auth.domain.Session;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Emite el par access token + refresh token de una sesión. */
@Component
class TokenFactory {

  private final TokenIssuer tokenIssuer;
  private final RefreshTokenRepository refreshTokens;

  TokenFactory(TokenIssuer tokenIssuer, RefreshTokenRepository refreshTokens) {
    this.tokenIssuer = tokenIssuer;
    this.refreshTokens = refreshTokens;
  }

  Issued issue(
      Session session, LoginClient client, String email, Authorization authorization, Instant now) {
    Instant accessExpiresAt = now.plus(client.accessTokenTtl());
    String accessToken =
        tokenIssuer.issue(
            new AccessTokenClaims(
                session.userId(),
                email,
                authorization.applicationCode(),
                client.clientId(),
                authorization.roles(),
                authorization.permissions(),
                now,
                accessExpiresAt,
                UUID.randomUUID(),
                session.id()));

    String refreshValue = RefreshTokenValues.generate();
    Instant refreshExpiresAt = now.plus(client.refreshTokenTtl());
    RefreshToken refreshToken =
        refreshTokens.save(
            RefreshToken.issue(
                session.id(), RefreshTokenValues.hash(refreshValue), refreshExpiresAt, now));

    return new Issued(
        new AuthTokens(
            accessToken,
            accessExpiresAt,
            client.accessTokenTtl().toSeconds(),
            refreshValue,
            refreshExpiresAt,
            authorization.applicationCode(),
            client.platform(),
            session.id()),
        refreshToken.id());
  }

  record Issued(AuthTokens tokens, UUID refreshTokenId) {}
}

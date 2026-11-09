package com.bank.gateway.auth;

import com.bank.gateway.config.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

/** Mints and verifies the gateway's HS256 demo access tokens. */
@Service
public class JwtService {

    private final AuthProperties properties;
    private final MACSigner signer;
    private final MACVerifier verifier;

    public JwtService(AuthProperties properties) throws JOSEException {
        this.properties = properties;
        byte[] secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.auth.secret must be at least 32 bytes for HS256");
        }
        this.signer = new MACSigner(secret);
        this.verifier = new MACVerifier(secret);
    }

    public Token mint(AuthProperties.User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getTtlMinutes() * 60);
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .subject(user.username())
                .jwtID(UUID.randomUUID().toString())
                .claim("role", user.role())
                .claim("name", user.displayName())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt));
        if (user.accountId() != null && !user.accountId().isBlank()) {
            builder.claim("accountId", user.accountId());
        }
        JWTClaimsSet claims = builder.build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign access token", e);
        }
        return new Token(jwt.serialize(), user.username(), user.role(),
                user.accountId(), user.displayName(),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public Claims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                throw new UnauthorizedException("Invalid token signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!properties.getIssuer().equals(claims.getIssuer())) {
                throw new UnauthorizedException("Invalid token issuer");
            }
            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.toInstant().isBefore(Instant.now())) {
                throw new UnauthorizedException("Token expired");
            }
            return new Claims(claims.getSubject(),
                    (String) claims.getClaim("role"),
                    (String) claims.getClaim("accountId"),
                    (String) claims.getClaim("name"),
                    expiry.toInstant().atOffset(ZoneOffset.UTC));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Malformed token: " + e.getMessage());
        }
    }

    public record Token(String token, String username, String role, String accountId,
                        String displayName, OffsetDateTime expiresAt) {
    }

    public record Claims(String username, String role, String accountId,
                         String displayName, OffsetDateTime expiresAt) {
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}

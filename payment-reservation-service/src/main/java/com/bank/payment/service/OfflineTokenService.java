package com.bank.payment.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Mints and verifies RS256 offline-payment tokens (v2 §4.2).
 *
 * <p>The demo generates a fresh RSA key pair on startup and publishes the public
 * key at {@code /reservations/jwks.json}; a production deployment would load a
 * stable key from a secret store so tokens survive restarts and verify across
 * replicas.</p>
 */
@Service
public class OfflineTokenService {

    private final String issuer;
    private final String keyId = UUID.randomUUID().toString();
    private KeyPair keyPair;

    public OfflineTokenService(@Value("${app.offline-token.issuer:ddbs-payment-reservation}") String issuer) {
        this.issuer = issuer;
    }

    @PostConstruct
    void generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate offline-token RSA key", e);
        }
    }

    public String mint(UUID reservationId, UUID accountId, BigDecimal amount,
                       OffsetDateTime expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(reservationId.toString())
                    .jwtID(reservationId.toString())
                    .claim("accountId", accountId.toString())
                    .claim("amount", amount.toPlainString())
                    .expirationTime(Date.from(expiresAt.toInstant()))
                    .issueTime(new Date())
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .keyID(keyId)
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign offline token", e);
        }
    }

    public Claims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier((RSAPublicKey) keyPair.getPublic()))) {
                throw new InvalidTokenException("Token signature is invalid");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())) {
                throw new InvalidTokenException("Token issuer mismatch");
            }
            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.toInstant().isBefore(java.time.Instant.now())) {
                throw new InvalidTokenException("Token has expired");
            }
            return new Claims(claims.getSubject(),
                    UUID.fromString((String) claims.getClaim("accountId")),
                    new BigDecimal((String) claims.getClaim("amount")),
                    expiry.toInstant().atOffset(java.time.ZoneOffset.UTC));
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("Malformed offline token: " + e.getMessage());
        }
    }

    public Map<String, Object> jwks() {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return Map.of("keys", java.util.List.of(key.toPublicJWK().toJSONObject()));
    }

    public record Claims(String reservationId, UUID accountId, BigDecimal amount,
                         OffsetDateTime expiresAt) {
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}

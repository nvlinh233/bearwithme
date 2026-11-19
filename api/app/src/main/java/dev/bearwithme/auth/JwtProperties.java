package dev.bearwithme.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code bearwithme.jwt.*}; secret and issuer come from environment variables (ADR-0001). */
@ConfigurationProperties("bearwithme.jwt")
record JwtProperties(String secret, String issuer, Duration ttl) {

    private static final int MIN_SECRET_BYTES = 32; // HS256 key must be at least 256 bits

    JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("bearwithme.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("bearwithme.jwt.issuer must be set");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("bearwithme.jwt.ttl must be positive");
        }
    }
}

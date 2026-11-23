package dev.bearwithme.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues HS256 access tokens (FR-A2). Subject = username, {@code uid} = user id. */
@Service
class TokenService {

    /** Custom claim with the user's database id, read by {@link CurrentUser}. */
    static final String USER_ID_CLAIM = "uid";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final UserAccountRepository users;

    TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock, UserAccountRepository users) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.users = users;
    }

    AccessToken issue(String username) {
        long userId = users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        // JWT timestamps have second precision
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(username)
                .claim(USER_ID_CLAIM, userId)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }

    record AccessToken(String value, Instant expiresAt) {
    }
}

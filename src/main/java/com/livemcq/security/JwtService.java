package com.livemcq.security;

import com.livemcq.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Creates and checks JSON Web Tokens.
 * A JWT is three Base64 parts: header.payload.signature. The payload carries the claims below; the
 * signature is an HMAC-SHA256 of header+payload made with our secret, so nobody can change a claim
 * (say role=STUDENT to ADMIN) without the signature breaking. The token is signed, NOT encrypted:
 * anyone can read the payload, so never put secrets in it.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;   // injected so tests can travel in time

    public JwtService(JwtProperties props, Clock clock) {
        byte[] secret = props.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("livemcq.jwt.secret must be at least 32 bytes long (HS256 needs 256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = props.ttl();
        this.clock = clock;
    }

    public IssuedToken issue(long userId, long institutionId, Role role) {
        Instant now = clock.instant();
        String token = Jwts.builder()
                .subject(Long.toString(userId))
                .claim("inst", institutionId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)                       // HS256, chosen from the key size
                .compact();
        return new IssuedToken(token, ttl.toSeconds());
    }

    /** Empty for anything that is not a valid, unexpired token signed with OUR key. Never throws. */
    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)                                  // rejects bad signature AND unsigned ("alg":"none") tokens
                    .clock(() -> Date.from(clock.instant()))          // expiry is judged by our clock
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long userId = Long.parseLong(claims.getSubject());
            long institutionId = ((Number) claims.get("inst")).longValue();
            Role role = Role.valueOf(claims.get("role", String.class));
            return Optional.of(new AuthPrincipal(userId, institutionId, role));
        } catch (RuntimeException e) {
            // JwtException (tampered / expired / malformed) and any claim that is missing or of the wrong type:
            // the input is untrusted, so every failure simply means "not authenticated".
            return Optional.empty();
        }
    }
}

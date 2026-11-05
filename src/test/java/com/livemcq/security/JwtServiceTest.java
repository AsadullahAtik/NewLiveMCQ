package com.livemcq.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livemcq.domain.Role;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring, no database. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC);

    private static JwtService service(String secret, Clock clock) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(8)), clock);
    }

    @Test
    void issuedTokenRoundTripsToTheSamePrincipal() {
        JwtService jwt = service(SECRET, NOW);
        IssuedToken t = jwt.issue(42, 7, Role.TEACHER);

        assertEquals(8 * 3600, t.expiresInSeconds());
        AuthPrincipal p = jwt.parse(t.value()).orElseThrow();
        assertEquals(42, p.userId());
        assertEquals(7, p.institutionId());
        assertEquals(Role.TEACHER, p.role());
    }

    @Test
    void tokenIsRejectedAfterItExpires() {
        String token = service(SECRET, NOW).issue(1, 1, Role.STUDENT).value();
        Clock sevenHoursLater = Clock.offset(NOW, Duration.ofHours(7));
        Clock nineHoursLater = Clock.offset(NOW, Duration.ofHours(9));

        assertTrue(service(SECRET, sevenHoursLater).parse(token).isPresent());
        assertTrue(service(SECRET, nineHoursLater).parse(token).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = service("another-secret-another-secret-0001", NOW).issue(1, 1, Role.ADMIN).value();
        assertTrue(service(SECRET, NOW).parse(forged).isEmpty());
    }

    @Test
    void changedSignatureIsRejected() {
        String token = service(SECRET, NOW).issue(1, 1, Role.STUDENT).value();
        String[] parts = token.split("\\.");
        // change the FIRST signature character: the last one may only hold padding bits and be ignored
        String badSig = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertTrue(service(SECRET, NOW).parse(parts[0] + "." + parts[1] + "." + badSig).isEmpty());
    }

    @Test
    void editedPayloadIsRejected() {
        JwtService jwt = service(SECRET, NOW);
        String student = jwt.issue(1, 1, Role.STUDENT).value();
        String admin = jwt.issue(1, 1, Role.ADMIN).value();
        // student's header + ADMIN's payload + student's signature = a "promoted" token
        String[] s = student.split("\\.");
        String[] a = admin.split("\\.");
        assertTrue(jwt.parse(s[0] + "." + a[1] + "." + s[2]).isEmpty());
    }

    @Test
    void unsignedTokenIsRejected() {
        String unsigned = Jwts.builder().subject("1").claim("inst", 1).claim("role", "ADMIN").compact();
        assertTrue(service(SECRET, NOW).parse(unsigned).isEmpty());
    }

    @Test
    void garbageIsRejectedWithoutThrowing() {
        JwtService jwt = service(SECRET, NOW);
        assertTrue(jwt.parse("").isEmpty());
        assertTrue(jwt.parse("not-a-jwt").isEmpty());
        assertTrue(jwt.parse("a.b.c").isEmpty());
    }

    @Test
    void tooShortSecretIsRefusedAtStartup() {
        assertThrows(IllegalStateException.class, () -> service("short", NOW));
    }
}

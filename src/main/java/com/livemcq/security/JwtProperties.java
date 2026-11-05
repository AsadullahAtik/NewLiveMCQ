package com.livemcq.security;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Binds "livemcq.jwt.*" from application.yml / environment variables into a typed, immutable record.
 * The secret has NO default in the main config: the app refuses to start without LIVEMCQ_JWT_SECRET,
 * so a forgotten secret can never silently become a well-known one in production.
 */
@Validated
@ConfigurationProperties(prefix = "livemcq.jwt")
public record JwtProperties(@NotBlank String secret, @DefaultValue("8h") Duration ttl) { }

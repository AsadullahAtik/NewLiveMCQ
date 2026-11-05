package com.livemcq.web;

import org.springframework.lang.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The ONE shape every error response has, so the web and Flutter clients write a single error parser.
 * "code" is for programs (stable, upper-case); "message" is for humans.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String code, String message, @Nullable Map<String, String> fieldErrors) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, null);
    }
}

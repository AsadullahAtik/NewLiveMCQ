package com.livemcq.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Student self sign-up. Only presence and size are checked here; the roll and password RULES
 * (digits only, minimum length) live in AuthService so they sit next to the code that enforces them.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 60) String institution,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 20) String roll,
        @NotBlank @Size(max = 200) String password,
        @NotNull Long sectionId) { }

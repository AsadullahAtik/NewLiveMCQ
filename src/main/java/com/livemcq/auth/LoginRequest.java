package com.livemcq.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the client sends to log in. A record = an immutable data carrier; the annotations are Bean Validation
 * rules that run before the controller body when the parameter is marked @Valid.
 * "institution" is the school/coaching slug (e.g. "demo"): the same roll can exist in different institutions.
 */
public record LoginRequest(
        @NotBlank(message = "Institution is required") @Size(max = 60) String institution,
        @NotBlank(message = "Roll is required") @Size(max = 20) String roll,
        @NotBlank(message = "Password is required") @Size(max = 200) String password) { }

package com.livemcq.security;

import com.livemcq.domain.Role;

/** Who is calling, as proven by a valid token. Controllers receive it via @AuthenticationPrincipal. */
public record AuthPrincipal(long userId, long institutionId, Role role) { }

package com.livemcq.auth;

public record LoginResponse(String token, String tokenType, long expiresInSeconds, UserView user) { }

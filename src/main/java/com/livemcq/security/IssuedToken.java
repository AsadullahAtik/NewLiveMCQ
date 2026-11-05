package com.livemcq.security;

public record IssuedToken(String value, long expiresInSeconds) { }

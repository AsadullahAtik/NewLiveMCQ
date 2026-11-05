package com.livemcq.domain;

/** Stored as text ("ADMIN", ...) so adding a role later never renumbers old rows. */
public enum Role {
    ADMIN,
    TEACHER,
    STUDENT
}

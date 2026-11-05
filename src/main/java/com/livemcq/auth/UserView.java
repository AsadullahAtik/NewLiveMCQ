package com.livemcq.auth;

import org.springframework.lang.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.livemcq.domain.Role;

/** The user as the client sees it. There is deliberately no password / hash field here. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserView(long id, String name, String roll, Role role, InstitutionView institution,
                       @Nullable Long classId, @Nullable String className, @Nullable Long sectionId, @Nullable String sectionName) {

    public record InstitutionView(long id, String name, String slug) { }
}

package com.livemcq.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livemcq.domain.AppUser;
import com.livemcq.domain.Institution;
import com.livemcq.domain.Role;
import com.livemcq.domain.SchoolClass;
import com.livemcq.domain.Section;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.InstitutionRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.repo.SectionRepository;
import com.livemcq.security.JwtProperties;
import com.livemcq.security.JwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whole web stack (security filter -> controller -> service -> real PostgreSQL), driven with MockMvc
 * (fake HTTP requests, no network port). @Transactional rolls back everything each test inserts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtProperties jwtProps;
    @Autowired InstitutionRepository institutions;
    @Autowired SchoolClassRepository classes;
    @Autowired SectionRepository sections;
    @Autowired AppUserRepository users;

    private AppUser student;

    @BeforeEach
    void seed() {
        Institution alpha = institutions.save(new Institution("Alpha Coaching", "alpha"));
        Institution beta = institutions.save(new Institution("Beta School", "beta"));
        SchoolClass nine = classes.save(new SchoolClass(alpha.getId(), "Class 9"));
        Section a = sections.save(new Section(nine, "A"));
        users.save(new AppUser(alpha.getId(), Role.ADMIN, "Alpha Admin", "admin", encoder.encode("alpha-pass"), null));
        users.save(new AppUser(alpha.getId(), Role.TEACHER, "Alpha Teacher", "teacher", encoder.encode("teach-pass"), null));
        student = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Alpha Student", "1001", encoder.encode("1234"), a));
        // the SAME roll "admin" in another institution, with a different password
        users.save(new AppUser(beta.getId(), Role.ADMIN, "Beta Admin", "admin", encoder.encode("beta-pass"), null));
    }

    // ---------- helpers ----------

    private ResultActions loginRequest(String institution, String roll, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("institution", institution, "roll", roll, "password", password));
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token(String institution, String roll, String password) throws Exception {
        String response = loginRequest(institution, roll, password)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    private ResultActions getWith(String url, String token) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    // ---------- login ----------

    @Test
    void loginReturnsTokenAndUserWithoutAnyPasswordData() throws Exception {
        loginRequest("alpha", "admin", "alpha-pass")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds", greaterThan(0)))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.roll").value("admin"))
                .andExpect(jsonPath("$.user.institution.slug").value("alpha"))
                .andExpect(content().string(not(containsString("$2a$"))))
                .andExpect(content().string(not(containsString("assword"))));
    }

    @Test
    void institutionAndRollAreCaseAndSpaceTolerant() throws Exception {
        loginRequest("  ALPHA ", " ADMIN ", "alpha-pass").andExpect(status().isOk());
    }

    @Test
    void passwordIsCaseSensitive() throws Exception {
        loginRequest("alpha", "admin", "ALPHA-PASS").andExpect(status().isUnauthorized());
    }

    @Test
    void everyKindOfBadLoginGetsExactlyTheSameAnswer() throws Exception {
        String[] answers = {
            loginRequest("alpha", "admin", "wrong").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn().getResponse().getContentAsString(),
            loginRequest("alpha", "nobody", "alpha-pass").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn().getResponse().getContentAsString(),
            loginRequest("no-such-school", "admin", "alpha-pass").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn().getResponse().getContentAsString(),
            loginRequest("alpha", "admin", "x".repeat(150)).andExpect(status().isUnauthorized())   // longer than BCrypt's 72 bytes
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn().getResponse().getContentAsString(),
        };
        String message = json.readTree(answers[0]).get("message").asText();
        for (String a : answers) {
            assertEquals(message, json.readTree(a).get("message").asText());
        }
    }

    @Test
    void sameRollInTwoInstitutionsNeverMixesThem() throws Exception {
        loginRequest("beta", "admin", "beta-pass").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.institution.slug").value("beta"));
        loginRequest("beta", "admin", "alpha-pass").andExpect(status().isUnauthorized());
        loginRequest("alpha", "admin", "beta-pass").andExpect(status().isUnauthorized());
    }

    @Test
    void missingFieldsGiveFieldByFieldValidationErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institution\":\"\",\"roll\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.institution", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.roll", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    void brokenJsonIsABadRequestNotAServerError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST_BODY"));
    }

    // ---------- /me and token checks ----------

    @Test
    void meReturnsTheLoggedInStudentWithClassAndSection() throws Exception {
        String t = token("alpha", "1001", "1234");
        getWith("/api/auth/me", t)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Student"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.className").value("Class 9"))
                .andExpect(jsonPath("$.sectionName").value("A"));
    }

    @Test
    void meForAdminHasNoClassFields() throws Exception {
        getWith("/api/auth/me", token("alpha", "admin", "alpha-pass"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").doesNotExist());
    }

    @Test
    void noTokenMeansUnauthorizedJson() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void garbageAndTamperedTokensAreUnauthorized() throws Exception {
        getWith("/api/auth/me", "garbage").andExpect(status().isUnauthorized());

        String[] parts = token("alpha", "1001", "1234").split("\\.");
        String badSig = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        getWith("/api/auth/me", parts[0] + "." + parts[1] + "." + badSig).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        Clock twoDaysAgo = Clock.fixed(Instant.now().minus(Duration.ofDays(2)), ZoneOffset.UTC);
        String old = new JwtService(jwtProps, twoDaysAgo).issue(student.getId(), student.getInstitutionId(), Role.STUDENT).value();
        getWith("/api/auth/me", old).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfADeletedAccountStopsWorking() throws Exception {
        String t = token("alpha", "1001", "1234");
        users.delete(student);
        getWith("/api/auth/me", t)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------- roles ----------

    @Test
    void adminAndTeacherMayEnterTheAdminArea() throws Exception {
        getWith("/api/admin/_probe", token("alpha", "admin", "alpha-pass")).andExpect(status().isOk());
        getWith("/api/admin/_probe", token("alpha", "teacher", "teach-pass")).andExpect(status().isOk());
    }

    @Test
    void studentIsForbiddenFromTheAdminArea() throws Exception {
        getWith("/api/admin/_probe", token("alpha", "1001", "1234"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void onlyStudentsMayEnterTheStudentArea() throws Exception {
        getWith("/api/student/_probe", token("alpha", "1001", "1234")).andExpect(status().isOk());
        getWith("/api/student/_probe", token("alpha", "admin", "alpha-pass")).andExpect(status().isForbidden());
    }

    @Test
    void protectedAreasNeedALogin() throws Exception {
        mvc.perform(get("/api/admin/_probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/student/_probe")).andExpect(status().isUnauthorized());
    }

    // ---------- CORS ----------

    @Test
    void browserPreflightFromTheWebAppIsAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void otherOriginsAreNotAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}

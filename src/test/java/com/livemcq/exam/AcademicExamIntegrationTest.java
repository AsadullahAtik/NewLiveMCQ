package com.livemcq.exam;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livemcq.domain.AppUser;
import com.livemcq.domain.Attempt;
import com.livemcq.domain.Exam;
import com.livemcq.domain.Institution;
import com.livemcq.domain.Role;
import com.livemcq.domain.SchoolClass;
import com.livemcq.domain.Section;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.AttemptRepository;
import com.livemcq.repo.ExamRepository;
import com.livemcq.repo.InstitutionRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.repo.SectionRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

/** Step 3a: sign-up, classes/sections, students and exams, through the real security + database stack. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AcademicExamIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired InstitutionRepository institutions;
    @Autowired SchoolClassRepository classes;
    @Autowired SectionRepository sections;
    @Autowired AppUserRepository users;
    @Autowired ExamRepository exams;
    @Autowired AttemptRepository attempts;
    @Autowired EntityManager em;

    private Institution alpha, beta;
    private SchoolClass alphaClass, betaClass;
    private Section alphaSection, betaSection;
    private AppUser alphaStudent;
    private String adminToken, teacherToken, studentToken, betaAdminToken;

    @BeforeEach
    void seed() throws Exception {
        alpha = institutions.save(new Institution("Alpha Coaching", "alpha"));
        beta = institutions.save(new Institution("Beta School", "beta"));
        alphaClass = classes.save(new SchoolClass(alpha.getId(), "Class 9"));
        betaClass = classes.save(new SchoolClass(beta.getId(), "Class 9"));   // same NAME, other institution: allowed
        alphaSection = sections.save(new Section(alphaClass, "A"));
        betaSection = sections.save(new Section(betaClass, "A"));
        users.save(new AppUser(alpha.getId(), Role.ADMIN, "Alpha Admin", "admin", encoder.encode("pw-admin"), null));
        users.save(new AppUser(alpha.getId(), Role.TEACHER, "Alpha Teacher", "teacher", encoder.encode("pw-teach"), null));
        alphaStudent = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Alpha Student", "1001", encoder.encode("1234"), alphaSection));
        users.save(new AppUser(beta.getId(), Role.ADMIN, "Beta Admin", "admin", encoder.encode("pw-beta"), null));
        adminToken = login("alpha", "admin", "pw-admin");
        teacherToken = login("alpha", "teacher", "pw-teach");
        studentToken = login("alpha", "1001", "1234");
        betaAdminToken = login("beta", "admin", "pw-beta");
    }

    // ---------- helpers ----------

    private String login(String inst, String roll, String pw) throws Exception {
        String body = json.writeValueAsString(Map.of("institution", inst, "roll", roll, "password", pw));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private JsonNode body(ResultActions a) throws Exception {
        return json.readTree(a.andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> register(String inst, String name, String roll, String pw, Object sectionId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("institution", inst); m.put("name", name); m.put("roll", roll); m.put("password", pw); m.put("sectionId", sectionId);
        return m;
    }

    private static Map<String, Object> question(String body, int correct) {
        return Map.of("body", body, "options", List.of("a", "b", "c", "d"), "correctIdx", correct);
    }

    private Map<String, Object> examRequest(long classId, int questionCount) {
        List<Object> qs = new ArrayList<>();
        for (int i = 1; i <= questionCount; i++) qs.add(question("Question " + i, i % 4));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Weekly Test"); m.put("classId", classId); m.put("durationMinutes", 30);
        m.put("marksPerQuestion", 1.5); m.put("negativeMarks", 0.25); m.put("questions", qs);
        return m;
    }

    private long createExam(String token, long classId, int n) throws Exception {
        return body(send(post("/api/admin/exams"), token, examRequest(classId, n)).andExpect(status().isCreated()))
                .get("exam").get("id").asLong();
    }

    // ---------- sign-up ----------

    @Test
    void studentCanRegisterWithBengaliDigitsAndIsLoggedInAtOnce() throws Exception {
        JsonNode res = body(send(post("/api/auth/register"), null,
                register("alpha", "  রাকিব হাসান ", "১২৩৪৫", "abcd", alphaSection.getId())).andExpect(status().isCreated()));
        assertEquals("12345", res.get("user").get("roll").asText());
        assertEquals("STUDENT", res.get("user").get("role").asText());
        assertEquals("Class 9", res.get("user").get("className").asText());
        send(get("/api/auth/me"), res.get("token").asText(), null).andExpect(status().isOk());
        login("alpha", "12345", "abcd");   // and the plain-digit roll logs in later
        String hash = users.findByRoll(alpha.getId(), "12345").orElseThrow().getPasswordHash();
        assertTrue(hash.startsWith("$2"), "password must be stored as a BCrypt hash");
    }

    @Test
    void registrationRulesAreEnforcedWithSpecificCodes() throws Exception {
        long sec = alphaSection.getId();
        for (String badRoll : List.of("1", "123456789", "12ab", "a1234")) {
            send(post("/api/auth/register"), null, register("alpha", "Some Name", badRoll, "abcd", sec))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ROLL_INVALID"));
        }
        send(post("/api/auth/register"), null, register("alpha", "Some Name", "5555", "abc", sec))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
        send(post("/api/auth/register"), null, register("alpha", "ab", "5555", "abcd", sec))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NAME_TOO_SHORT"));
        send(post("/api/auth/register"), null, register("alpha", "Some Name", "1001", "abcd", sec))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLL_TAKEN"));
        send(post("/api/auth/register"), null, register("alpha", "Some Name", "5555", "abcd", betaSection.getId()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SECTION_INVALID"));
        send(post("/api/auth/register"), null, register("nope", "Some Name", "5555", "abcd", sec))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INSTITUTION_NOT_FOUND"));
        send(post("/api/auth/register"), null, register("alpha", "Some Name", "5555", "abcd", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.sectionId", notNullValue()));
    }

    @Test
    void sameRollMayRegisterInAnotherInstitution() throws Exception {
        send(post("/api/auth/register"), null, register("beta", "Beta Kid", "1001", "abcd", betaSection.getId()))
                .andExpect(status().isCreated());
    }

    // ---------- classes & sections ----------

    @Test
    void publicSignupFormCanListClassesWithoutLogin() throws Exception {
        send(get("/api/public/institutions/alpha/classes"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Class 9"))
                .andExpect(jsonPath("$[0].sections[0].name").value("A"));
        send(get("/api/public/institutions/nope/classes"), null, null).andExpect(status().isNotFound());
    }

    @Test
    void adminCreatesClassWithDefaultSectionAndDuplicatesAreRejected() throws Exception {
        send(post("/api/admin/classes"), adminToken, Map.of("name", " Class 10 "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Class 10"))
                .andExpect(jsonPath("$.sections", hasSize(1)))
                .andExpect(jsonPath("$.sections[0].name").value("ক"));
        send(post("/api/admin/classes"), teacherToken, Map.of("name", "Class 10"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLASS_EXISTS"));
        send(post("/api/admin/classes"), adminToken, Map.of("name", "X"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLASS_NAME_TOO_SHORT"));
        send(post("/api/admin/classes"), adminToken, Map.of("name", "  "))
                .andExpect(status().isBadRequest());
        send(post("/api/admin/classes"), studentToken, Map.of("name", "Class 11"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sectionsCanBeAddedOnlyToOwnClassesAndOnlyOnce() throws Exception {
        send(post("/api/admin/classes/" + alphaClass.getId() + "/sections"), adminToken, Map.of("name", "B"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("B"));
        send(post("/api/admin/classes/" + alphaClass.getId() + "/sections"), adminToken, Map.of("name", "B"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SECTION_EXISTS"));
        send(post("/api/admin/classes/" + betaClass.getId() + "/sections"), adminToken, Map.of("name", "B"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void classListShowsCountsAndOnlyOwnInstitution() throws Exception {
        long examId = createExam(adminToken, alphaClass.getId(), 2);
        send(get("/api/admin/classes"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentCount").value(1))
                .andExpect(jsonPath("$[0].examCount").value(1))
                .andExpect(jsonPath("$[0].sections[0].studentCount").value(1));
        send(get("/api/admin/classes"), betaAdminToken, null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].examCount").value(0))
                .andExpect(jsonPath("$[0].studentCount").value(0));
        assertTrue(examId > 0);
    }

    // ---------- students ----------

    @Test
    void studentListAndMoveBetweenSections() throws Exception {
        Section b = sections.save(new Section(alphaClass, "B"));
        send(get("/api/admin/students"), adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roll").value("1001")).andExpect(jsonPath("$[0].sectionName").value("A"))
                .andExpect(jsonPath("$[0].attempts").value(0));
        send(patch("/api/admin/students/" + alphaStudent.getId() + "/enrollment"), adminToken, Map.of("sectionId", b.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sectionName").value("B"));
        // another institution's section, or student, is never reachable
        send(patch("/api/admin/students/" + alphaStudent.getId() + "/enrollment"), adminToken, Map.of("sectionId", betaSection.getId()))
                .andExpect(status().isUnprocessableEntity());
        send(patch("/api/admin/students/" + alphaStudent.getId() + "/enrollment"), betaAdminToken, Map.of("sectionId", betaSection.getId()))
                .andExpect(status().isNotFound());
        send(get("/api/admin/students"), betaAdminToken, null).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- exams ----------

    @Test
    void createdExamHasCorrectTotalsAndQuestionsInOrder() throws Exception {
        JsonNode d = body(send(post("/api/admin/exams"), adminToken, examRequest(alphaClass.getId(), 4)).andExpect(status().isCreated()));
        JsonNode e = d.get("exam");
        assertEquals("Class 9", e.get("className").asText());
        assertEquals(4, e.get("questionCount").asInt());
        assertEquals(0, new java.math.BigDecimal("6.00").compareTo(e.get("fullMarks").decimalValue()));   // 4 x 1.5
        assertEquals(0, e.get("bonusMarks").decimalValue().signum());
        assertEquals(false, e.get("published").asBoolean());
        assertEquals(4, d.get("questions").size());
        assertEquals("Question 1", d.get("questions").get(0).get("body").asText());
        assertEquals("Question 4", d.get("questions").get(3).get("body").asText());
        assertEquals(1, d.get("questions").get(0).get("correctIdx").asInt());
        assertEquals(4, d.get("questions").get(0).get("options").size());
    }

    @Test
    void teacherMayCreateButStudentMayNot() throws Exception {
        send(post("/api/admin/exams"), teacherToken, examRequest(alphaClass.getId(), 1)).andExpect(status().isCreated());
        send(post("/api/admin/exams"), studentToken, examRequest(alphaClass.getId(), 1)).andExpect(status().isForbidden());
    }

    @Test
    void invalidExamsAreRejectedWithFieldErrors() throws Exception {
        long c = alphaClass.getId();
        Map<String, Object> m;

        m = examRequest(c, 1); m.put("durationMinutes", 0);
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes", notNullValue()));
        m = examRequest(c, 1); m.put("durationMinutes", 181);
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isBadRequest());
        m = examRequest(c, 1); m.put("marksPerQuestion", 0);
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.marksPerQuestion", notNullValue()));
        m = examRequest(c, 1); m.put("negativeMarks", -1);
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.negativeMarks", notNullValue()));
        m = examRequest(c, 1); m.put("questions", List.of());
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.questions", notNullValue()));
        m = examRequest(c, 1); m.put("title", "ab");
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TITLE_TOO_SHORT"));
        m = examRequest(c, 1);
        m.put("questions", List.of(Map.of("body", "Q", "options", List.of("a", "b", "c"), "correctIdx", 0)));   // 3 options
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['questions[0].options']", notNullValue()));
        m = examRequest(c, 1);
        m.put("questions", List.of(Map.of("body", "Q", "options", List.of("a", "b", "c", "d"), "correctIdx", 4)));
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['questions[0].correctIdx']", notNullValue()));
        m = examRequest(c, 1);
        m.put("questions", List.of(Map.of("body", "Q", "options", List.of("a", " ", "c", "d"), "correctIdx", 0)));   // blank option
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isBadRequest());
        m = examRequest(betaClass.getId(), 1);   // another institution's class
        send(post("/api/admin/exams"), adminToken, m).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLASS_INVALID"));
        assertEquals(0, exams.findByInstitutionIdOrderByCreatedAtDescIdDesc(alpha.getId()).size(), "nothing may be saved on failure");
    }

    @Test
    void examsAreListedNewestFirstAndNeverAcrossInstitutions() throws Exception {
        createExam(adminToken, alphaClass.getId(), 1);
        Map<String, Object> second = examRequest(alphaClass.getId(), 2);
        second.put("title", "Second Test");
        send(post("/api/admin/exams"), adminToken, second).andExpect(status().isCreated());
        createExam(betaAdminToken, betaClass.getId(), 1);

        send(get("/api/admin/exams"), adminToken, null)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Second Test"));
        send(get("/api/admin/exams"), betaAdminToken, null).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void anotherInstitutionCanNeitherSeeNorDeleteMyExam() throws Exception {
        long id = createExam(adminToken, alphaClass.getId(), 1);
        send(get("/api/admin/exams/" + id), betaAdminToken, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXAM_NOT_FOUND"));
        send(delete("/api/admin/exams/" + id), betaAdminToken, null).andExpect(status().isNotFound());
        send(get("/api/admin/exams/" + id), adminToken, null).andExpect(status().isOk());
    }

    @Test
    void deletingAnExamRemovesItsQuestionsAndAttempts() throws Exception {
        long id = createExam(adminToken, alphaClass.getId(), 3);
        Exam exam = exams.findById(id).orElseThrow();
        Attempt attempt = attempts.saveAndFlush(new Attempt(exam, alphaStudent, 120));
        em.detach(attempt);   // the request below runs in this same transaction; keep the stale row out of its way
        assertEquals(1, attempts.countByExamId(id));

        send(delete("/api/admin/exams/" + id), adminToken, null).andExpect(status().isNoContent());
        send(get("/api/admin/exams/" + id), adminToken, null).andExpect(status().isNotFound());
        exams.flush();
        assertEquals(0, attempts.countByExamId(id));
        send(delete("/api/admin/exams/" + id), adminToken, null).andExpect(status().isNotFound());
    }

    @Test
    void questionsCanBeReplacedBeforeAnyoneAnswers() throws Exception {
        long id = createExam(adminToken, alphaClass.getId(), 3);
        Map<String, Object> req = Map.of("questions", List.of(question("New one", 2), question("New two", 3)));
        send(put("/api/admin/exams/" + id + "/questions"), adminToken, req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exam.questionCount").value(2))
                .andExpect(jsonPath("$.questions[0].body").value("New one"))
                .andExpect(jsonPath("$.questions[1].correctIdx").value(3));
        send(get("/api/admin/exams/" + id), adminToken, null)
                .andExpect(jsonPath("$.questions", hasSize(2)));
        send(put("/api/admin/exams/" + id + "/questions"), adminToken, Map.of("questions", List.of()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void questionsAreLockedOncePublishedOrAnswered() throws Exception {
        long published = createExam(adminToken, alphaClass.getId(), 2);
        exams.findById(published).orElseThrow().publish();
        send(put("/api/admin/exams/" + published + "/questions"), adminToken, Map.of("questions", List.of(question("x", 0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_PUBLISHED"));

        long answered = createExam(adminToken, alphaClass.getId(), 2);
        attempts.saveAndFlush(new Attempt(exams.findById(answered).orElseThrow(), alphaStudent, 60));
        send(put("/api/admin/exams/" + answered + "/questions"), adminToken, Map.of("questions", List.of(question("x", 0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_HAS_ATTEMPTS"));
        send(get("/api/admin/exams/" + answered), adminToken, null)
                .andExpect(jsonPath("$.questions", hasSize(2)))
                .andExpect(jsonPath("$.exam.attempts").value(1));
    }

    @Test
    void protectedRoutesNeedLogin() throws Exception {
        send(get("/api/admin/exams"), null, null).andExpect(status().isUnauthorized());
        send(get("/api/admin/classes"), null, null).andExpect(status().isUnauthorized());
        send(get("/api/admin/students"), studentToken, null).andExpect(status().isForbidden());
    }
}

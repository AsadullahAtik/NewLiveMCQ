package com.livemcq.exam;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livemcq.domain.AppUser;
import com.livemcq.domain.AttemptAnswer;
import com.livemcq.domain.Exam;
import com.livemcq.domain.Institution;
import com.livemcq.domain.Role;
import com.livemcq.domain.SchoolClass;
import com.livemcq.domain.Section;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.AttemptAnswerRepository;
import com.livemcq.repo.AttemptRepository;
import com.livemcq.repo.ExamRepository;
import com.livemcq.repo.ExamSessionRepository;
import com.livemcq.repo.InstitutionRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.repo.SectionRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Step 3b: list, start (server-side timer), resume, submit, and every rule around them. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentExamIntegrationTest {

    /** Replaces the real clock in this test class only. @Primary makes Spring pick it. */
    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired InstitutionRepository institutions;
    @Autowired SchoolClassRepository classes;
    @Autowired SectionRepository sections;
    @Autowired AppUserRepository users;
    @Autowired ExamRepository exams;
    @Autowired ExamSessionRepository sessions;
    @Autowired AttemptRepository attempts;
    @Autowired AttemptAnswerRepository answers;

    private Institution alpha, beta;
    private SchoolClass nine, ten, betaClass;
    private AppUser rafi, nila;
    private String rafiToken, nilaToken, adminToken, betaStudentToken, tenStudentToken;
    private Exam exam;      // Class 9, 3 questions, 30 minutes, 1 mark each, 0.25 negative
    private Exam tenExam;   // Class 10

    @BeforeEach
    void seed() throws Exception {
        alpha = institutions.save(new Institution("Alpha Coaching", "alpha"));
        beta = institutions.save(new Institution("Beta School", "beta"));
        nine = classes.save(new SchoolClass(alpha.getId(), "Class 9"));
        ten = classes.save(new SchoolClass(alpha.getId(), "Class 10"));
        betaClass = classes.save(new SchoolClass(beta.getId(), "Class 9"));
        Section nineA = sections.save(new Section(nine, "A"));
        Section tenA = sections.save(new Section(ten, "A"));
        Section betaA = sections.save(new Section(betaClass, "A"));
        users.save(new AppUser(alpha.getId(), Role.ADMIN, "Alpha Admin", "admin", encoder.encode("pw-admin"), null));
        rafi = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Rafi Student", "1001", encoder.encode("1234"), nineA));
        nila = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Nila Student", "1002", encoder.encode("1234"), nineA));
        users.save(new AppUser(alpha.getId(), Role.STUDENT, "Tanvir Student", "2001", encoder.encode("1234"), tenA));
        users.save(new AppUser(beta.getId(), Role.STUDENT, "Beta Student", "1001", encoder.encode("1234"), betaA));
        rafiToken = login("alpha", "1001");
        nilaToken = login("alpha", "1002");
        tenStudentToken = login("alpha", "2001");
        betaStudentToken = login("beta", "1001");
        adminToken = login("alpha", "admin", "pw-admin");

        exam = newExam(nine, "Weekly Test");
        tenExam = newExam(ten, "Class 10 Test");
    }

    // ---------- helpers ----------

    private Exam newExam(SchoolClass c, String title) {
        Exam e = new Exam(c, title, 30, new java.math.BigDecimal("1.00"), new java.math.BigDecimal("0.25"));
        for (int i = 0; i < 3; i++) e.addQuestion("Question " + (i + 1), i, List.of("a", "b", "c", "d"));
        return exams.saveAndFlush(e);
    }

    private String login(String inst, String roll) throws Exception { return login(inst, roll, "1234"); }

    private String login(String inst, String roll, String pw) throws Exception {
        String body = json.writeValueAsString(Map.of("institution", inst, "roll", roll, "password", pw));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private ResultActions start(String token, Exam e) throws Exception {
        return send(post("/api/student/exams/" + e.getId() + "/start"), token, null);
    }

    private ResultActions submit(String token, Exam e, Integer... picked) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answers", new ArrayList<>(Arrays.asList(picked)));
        return send(post("/api/student/exams/" + e.getId() + "/submit"), token, body);
    }

    private JsonNode body(ResultActions a) throws Exception {
        return json.readTree(a.andReturn().getResponse().getContentAsString());
    }

    // ---------- list ----------

    @Test
    void listShowsOnlyMyClassExamsWithStatus() throws Exception {
        send(get("/api/student/exams"), rafiToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Weekly Test"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].questionCount").value(3))
                .andExpect(jsonPath("$[0].fullMarks").value(3.0))
                .andExpect(content().string(not(containsString("correctIdx"))));
        send(get("/api/student/exams"), tenStudentToken, null)
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].title").value("Class 10 Test"));
        send(get("/api/student/exams"), betaStudentToken, null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listStatusFollowsTheExamLifecycle() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(10));
        send(get("/api/student/exams"), rafiToken, null)
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].remainingSeconds").value(20 * 60));

        clock.advance(Duration.ofMinutes(21));   // 31 minutes after start, nothing submitted
        send(get("/api/student/exams"), rafiToken, null).andExpect(jsonPath("$[0].status").value("EXPIRED"));

        submitFresh(nilaToken);
        send(get("/api/student/exams"), nilaToken, null).andExpect(jsonPath("$[0].status").value("SUBMITTED"));

        exam.publish();
        send(get("/api/student/exams"), rafiToken, null).andExpect(jsonPath("$[0].status").value("MISSED"));
    }

    private void submitFresh(String token) throws Exception {
        // Nila starts late enough that her own 30 minutes are still running
        start(token, exam).andExpect(status().isOk());
        submit(token, exam, 0, 1, 2).andExpect(status().isOk());
    }

    // ---------- start ----------

    @Test
    void startReturnsThePaperWithoutAnswersAndWithServerTimes() throws Exception {
        JsonNode p = body(start(rafiToken, exam).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("correctIdx")))));
        assertEquals(3, p.get("questions").size());
        assertEquals("Question 1", p.get("questions").get(0).get("body").asText());
        assertEquals(4, p.get("questions").get(0).get("options").size());
        assertEquals(30 * 60, p.get("remainingSeconds").asLong());
        assertEquals(Duration.ofMinutes(30),
                Duration.between(java.time.Instant.parse(p.get("startedAt").asText()), java.time.Instant.parse(p.get("deadlineAt").asText())));
        assertEquals(1, sessions.countByExamId(exam.getId()));
    }

    @Test
    void startingAgainResumesWithTheRemainingTimeNotAFreshClock() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(12));
        JsonNode again = body(start(rafiToken, exam).andExpect(status().isOk()));
        assertEquals(18 * 60, again.get("remainingSeconds").asLong());
        assertEquals(1, sessions.countByExamId(exam.getId()), "no second session may be created");
    }

    @Test
    void startAfterTheDeadlineIsRefused() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(31));
        start(rafiToken, exam).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIME_EXPIRED"));
    }

    @Test
    void examOfAnotherClassOrInstitutionCannotBeStarted() throws Exception {
        start(rafiToken, tenExam).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_YOUR_CLASS"));
        start(betaStudentToken, exam).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXAM_NOT_FOUND"));
        assertEquals(0, sessions.countByExamId(exam.getId()) + sessions.countByExamId(tenExam.getId()));
    }

    @Test
    void publishedExamCannotBeStarted() throws Exception {
        exam.publish();
        start(rafiToken, exam).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_CLOSED"));
    }

    @Test
    void onlyStudentsMayStart() throws Exception {
        start(adminToken, exam).andExpect(status().isForbidden());
        send(post("/api/student/exams/" + exam.getId() + "/start"), null, null).andExpect(status().isUnauthorized());
    }

    // ---------- submit ----------

    @Test
    void submitSavesOnlyAnsweredQuestionsAndMeasuresTimeOnTheServer() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(5));
        submit(rafiToken, exam, 0, null, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.timeSec").value(300));

        var attempt = attempts.findByExamIdAndUserId(exam.getId(), rafi.getId()).orElseThrow();
        assertEquals(300, attempt.getTimeSec());
        List<AttemptAnswer> rows = answers.findByIdAttemptId(attempt.getId());
        assertEquals(2, rows.size(), "the blank question has no row");
        assertEquals(List.of(0, 3), rows.stream().map(AttemptAnswer::getChosenIdx).sorted().toList());
    }

    @Test
    void timeTakenIsAtLeastOneSecondAndNeverMoreThanTheDuration() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        submit(rafiToken, exam, null, null, null).andExpect(status().isOk()).andExpect(jsonPath("$.timeSec").value(1));

        start(nilaToken, exam).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(30).plusSeconds(10));   // late, but inside the 15 s grace
        submit(nilaToken, exam, 1, 1, 1).andExpect(status().isOk()).andExpect(jsonPath("$.timeSec").value(30 * 60));
    }

    @Test
    void submitAfterDeadlinePlusGraceIsRefusedAndNothingIsSaved() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(30).plusSeconds(16));
        submit(rafiToken, exam, 0, 1, 2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIME_EXPIRED"));
        assertEquals(0, attempts.countByExamId(exam.getId()));
    }

    @Test
    void submitWithoutStartingIsRefused() throws Exception {
        submit(rafiToken, exam, 0, 1, 2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_STARTED"));
    }

    @Test
    void secondSubmitAndRestartAfterSubmitAreRefused() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        submit(rafiToken, exam, 0, 1, 2).andExpect(status().isOk());
        submit(rafiToken, exam, 3, 3, 3).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
        start(rafiToken, exam).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
        assertEquals(1, attempts.countByExamId(exam.getId()));
        assertEquals(3, answers.findByIdAttemptId(attempts.findByExamIdAndUserId(exam.getId(), rafi.getId()).orElseThrow().getId()).size(),
                "the first submission is kept untouched");
    }

    @Test
    void badAnswerListsAreRejectedAndKeepTheExamOpen() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        submit(rafiToken, exam, 0, 1).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ANSWER_COUNT"));
        submit(rafiToken, exam, 0, 1, 2, 3).andExpect(status().isUnprocessableEntity());
        submit(rafiToken, exam, 0, 4, 2).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ANSWER_INVALID"));
        submit(rafiToken, exam, 0, -1, 2).andExpect(status().isUnprocessableEntity());
        send(post("/api/student/exams/" + exam.getId() + "/submit"), rafiToken, Map.of()).andExpect(status().isBadRequest());
        assertEquals(0, attempts.countByExamId(exam.getId()));
        submit(rafiToken, exam, 0, 1, 2).andExpect(status().isOk());   // a correct retry still works
    }

    @Test
    void cannotSubmitToAnotherClassOrAfterPublishing() throws Exception {
        submit(rafiToken, tenExam, 0, 1, 2).andExpect(status().isForbidden());
        start(rafiToken, exam).andExpect(status().isOk());
        exam.publish();
        submit(rafiToken, exam, 0, 1, 2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_CLOSED"));
    }

    @Test
    void oneStudentsSubmitDoesNotAffectAnother() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        start(nilaToken, exam).andExpect(status().isOk());
        submit(rafiToken, exam, 0, 1, 2).andExpect(status().isOk());
        send(get("/api/student/exams"), nilaToken, null).andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
        submit(nilaToken, exam, 3, 3, 3).andExpect(status().isOk());
        assertEquals(2, attempts.countByExamId(exam.getId()));
    }

    @Test
    void adminCannotEditQuestionsOnceAStudentStarted() throws Exception {
        start(rafiToken, exam).andExpect(status().isOk());
        Map<String, Object> q = Map.of("body", "x", "options", List.of("a", "b", "c", "d"), "correctIdx", 0);
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/exams/" + exam.getId() + "/questions"),
                adminToken, Map.of("questions", List.of(q)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_STARTED"));
    }
}

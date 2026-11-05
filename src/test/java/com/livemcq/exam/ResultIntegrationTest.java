package com.livemcq.exam;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livemcq.domain.AppUser;
import com.livemcq.domain.Attempt;
import com.livemcq.domain.AttemptAnswer;
import com.livemcq.domain.Exam;
import com.livemcq.domain.Institution;
import com.livemcq.domain.Question;
import com.livemcq.domain.Role;
import com.livemcq.domain.SchoolClass;
import com.livemcq.domain.Section;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.AttemptAnswerRepository;
import com.livemcq.repo.AttemptRepository;
import com.livemcq.repo.ExamRepository;
import com.livemcq.repo.InstitutionRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.repo.SectionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 4: publishing, bonus, leaderboards, answer sheets, reports, student results.
 *
 * The test and every request share ONE transaction, so before each request we flush pending changes and
 * clear Hibernate's first-level cache. (In production each request has its own transaction and cache, so
 * this problem does not exist; here it would show stale rows from the read-only database views.)
 *
 * Data (Class 9, exam A has 4 questions, correct options 0,1,2,3; 1 mark each, -0.25 per wrong):
 *   Rafi   picks 0,1,2,3        -> 4 correct               = 4.00 in 100 s
 *   Nila   picks 0,1,0,blank    -> 2 correct, 1 wrong      = 1.75 in 200 s
 *   Sara   picks 0,1,0,blank    -> identical to Nila       = 1.75 in 200 s   (tie: both rank 2)
 *   Tanvir picks 1,0,3,2        -> 0 correct, 4 wrong      = 0.00 (never below zero) in 150 s
 *   Absent took nothing.
 * Exam B (2 questions, correct 0,1): Rafi 0,1 = 2.00 in 50 s; Nila 0,0 = 0.75 in 60 s.
 * Exam C: nobody took it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResultIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired InstitutionRepository institutions;
    @Autowired SchoolClassRepository classes;
    @Autowired SectionRepository sections;
    @Autowired AppUserRepository users;
    @Autowired ExamRepository exams;
    @Autowired AttemptRepository attempts;
    @Autowired AttemptAnswerRepository answers;

    private long alphaId, nineId, nineSectionId, tenId;
    private long rafiId, nilaId, saraId, tanvirId, absentId, adminId;
    private long examA, examB, examC;
    private String adminToken, betaAdminToken, rafiToken, nilaToken, absentToken, tenToken;

    @BeforeEach
    void seed() throws Exception {
        Institution alpha = institutions.save(new Institution("Alpha Coaching", "alpha"));
        Institution beta = institutions.save(new Institution("Beta School", "beta"));
        SchoolClass nine = classes.save(new SchoolClass(alpha.getId(), "Class 9"));
        SchoolClass ten = classes.save(new SchoolClass(alpha.getId(), "Class 10"));
        Section nineA = sections.save(new Section(nine, "A"));
        Section tenA = sections.save(new Section(ten, "A"));
        alphaId = alpha.getId(); nineId = nine.getId(); nineSectionId = nineA.getId(); tenId = ten.getId();

        adminId = users.save(new AppUser(alpha.getId(), Role.ADMIN, "Alpha Admin", "admin", encoder.encode("pw"), null)).getId();
        AppUser rafi = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Rafi", "101", encoder.encode("1234"), nineA));
        AppUser nila = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Nila", "102", encoder.encode("1234"), nineA));
        AppUser sara = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Sara", "103", encoder.encode("1234"), nineA));
        AppUser tanvir = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Tanvir", "104", encoder.encode("1234"), nineA));
        AppUser absent = users.save(new AppUser(alpha.getId(), Role.STUDENT, "Absent One", "105", encoder.encode("1234"), nineA));
        users.save(new AppUser(alpha.getId(), Role.STUDENT, "Ten Kid", "201", encoder.encode("1234"), tenA));
        users.save(new AppUser(beta.getId(), Role.ADMIN, "Beta Admin", "admin", encoder.encode("pw"), null));
        rafiId = rafi.getId(); nilaId = nila.getId(); saraId = sara.getId(); tanvirId = tanvir.getId(); absentId = absent.getId();

        Exam a = exam(nine, "Exam A", 0, 1, 2, 3);
        attempt(a, rafi, 100, 0, 1, 2, 3);
        attempt(a, nila, 200, 0, 1, 0, null);
        attempt(a, sara, 200, 0, 1, 0, null);
        attempt(a, tanvir, 150, 1, 0, 3, 2);
        Thread.sleep(5);   // so "latest submitted first" has a clear order
        Exam b = exam(nine, "Exam B", 0, 1);
        attempt(b, rafi, 50, 0, 1);
        attempt(b, nila, 60, 0, 0);
        Exam c = exam(nine, "Exam C");   // no questions needed? -> needs at least the schema's minimum; see exam()
        examA = a.getId(); examB = b.getId(); examC = c.getId();

        adminToken = login("alpha", "admin", "pw");
        betaAdminToken = login("beta", "admin", "pw");
        rafiToken = login("alpha", "101", "1234");
        nilaToken = login("alpha", "102", "1234");
        absentToken = login("alpha", "105", "1234");
        tenToken = login("alpha", "201", "1234");
        em.flush();
        em.clear();
    }

    // ---------- helpers ----------

    private Exam exam(SchoolClass c, String title, int... correct) {
        Exam e = new Exam(c, title, 30, new BigDecimal("1.00"), new BigDecimal("0.25"));
        int[] answersKey = correct.length == 0 ? new int[] {0} : correct;   // exam C: one question, nobody answers
        for (int i = 0; i < answersKey.length; i++) e.addQuestion("Q" + (i + 1), answersKey[i], List.of("a", "b", "c", "d"));
        return exams.saveAndFlush(e);
    }

    private void attempt(Exam e, AppUser u, int timeSec, Integer... picks) {
        Attempt at = attempts.saveAndFlush(new Attempt(e, u, timeSec));
        List<AttemptAnswer> rows = new ArrayList<>();
        for (int i = 0; i < picks.length; i++) {
            if (picks[i] != null) {
                Question q = e.getQuestions().get(i);
                rows.add(new AttemptAnswer(at.getId(), q.getId(), e.getId(), picks[i]));
            }
        }
        answers.saveAllAndFlush(rows);
    }

    private String login(String inst, String roll, String pw) throws Exception {
        String body = json.writeValueAsString(Map.of("institution", inst, "roll", roll, "password", pw));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    /** Every request first writes pending changes and empties the cache (see the class comment). */
    private ResultActions send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        em.flush();
        em.clear();
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private ResultActions fetch(String url, String token) throws Exception { return send(get(url), token, null); }

    private void publish(long examId) throws Exception {
        send(post("/api/admin/exams/" + examId + "/publish"), adminToken, null).andExpect(status().isOk());
    }

    private ResultActions bonus(long examId, Object value) throws Exception {
        return send(post("/api/admin/exams/" + examId + "/bonus"), adminToken, Map.of("bonus", value));
    }

    // ---------- publish ----------

    @Test
    void publishNeedsAtLeastOneAttemptAndCanBeUndone() throws Exception {
        send(post("/api/admin/exams/" + examC + "/publish"), adminToken, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NO_ATTEMPTS"));
        send(post("/api/admin/exams/" + examA + "/publish"), adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
        assertTrue(exams.findById(examA).orElseThrow().isPublished());
        send(post("/api/admin/exams/" + examA + "/publish"), adminToken, null).andExpect(status().isOk());   // repeating is harmless
        send(delete("/api/admin/exams/" + examA + "/publish"), adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(false));
        assertTrue(!exams.findById(examA).orElseThrow().isPublished());
    }

    @Test
    void onlyStaffMayPublishOrGiveBonus() throws Exception {
        send(post("/api/admin/exams/" + examA + "/publish"), rafiToken, null).andExpect(status().isForbidden());
        send(post("/api/admin/exams/" + examA + "/bonus"), rafiToken, Map.of("bonus", 1)).andExpect(status().isForbidden());
        send(post("/api/admin/exams/" + examA + "/publish"), null, null).andExpect(status().isUnauthorized());
    }

    // ---------- leaderboard, ranks, ties ----------

    @Test
    void adminLeaderboardRanksByMarksThenTimeAndSharesTiedRanks() throws Exception {
        fetch("/api/admin/exams/" + examA + "/leaderboard", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants").value(4))
                .andExpect(jsonPath("$.exam.fullMarks").value(4.0))
                .andExpect(jsonPath("$.rows[*].rank", contains(1, 2, 2, 4)))
                .andExpect(jsonPath("$.rows[*].marks", contains(4.0, 1.75, 1.75, 0.0)))
                .andExpect(jsonPath("$.rows[*].name", contains("Rafi", "Nila", "Sara", "Tanvir")))
                .andExpect(jsonPath("$.rows[0].correct").value(4))
                .andExpect(jsonPath("$.rows[1].wrong").value(1))
                .andExpect(jsonPath("$.rows[1].blank").value(1))
                .andExpect(jsonPath("$.rows[3].wrong").value(4))
                .andExpect(jsonPath("$.rows[0].userId").value(rafiId));
    }

    @Test
    void fasterTimeBeatsEqualMarks() throws Exception {
        // Nila is made 10 s faster than Sara -> Nila rank 2, Sara rank 3 (no more tie)
        Attempt nilaAttempt = attempts.findByExamIdAndUserId(examA, nilaId).orElseThrow();
        em.createQuery("update Attempt a set a.timeSec = 190 where a.id = :id").setParameter("id", nilaAttempt.getId()).executeUpdate();
        fetch("/api/admin/exams/" + examA + "/leaderboard", adminToken)
                .andExpect(jsonPath("$.rows[*].rank", contains(1, 2, 3, 4)))
                .andExpect(jsonPath("$.rows[*].name", contains("Rafi", "Nila", "Sara", "Tanvir")));
    }

    // ---------- bonus ----------

    @Test
    void bonusRaisesEveryMarkAndTheFullMarksTogetherAndKeepsRanks() throws Exception {
        bonus(examA, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.bonus").value(2.0)).andExpect(jsonPath("$.updated").value(4))
                .andExpect(jsonPath("$.fullMarks").value(6.0));
        fetch("/api/admin/exams/" + examA + "/leaderboard", adminToken)
                .andExpect(jsonPath("$.exam.fullMarks").value(6.0))
                .andExpect(jsonPath("$.exam.bonusMarks").value(2.0))
                .andExpect(jsonPath("$.rows[*].marks", contains(6.0, 3.75, 3.75, 2.0)))   // even the 0.00 student gets it
                .andExpect(jsonPath("$.rows[*].rank", contains(1, 2, 2, 4)));
    }

    @Test
    void bonusReplacesThePreviousBonusAndZeroRemovesIt() throws Exception {
        bonus(examA, 2).andExpect(status().isOk());
        bonus(examA, 5).andExpect(status().isOk());   // NOT 2 + 5
        fetch("/api/admin/exams/" + examA + "/leaderboard", adminToken)
                .andExpect(jsonPath("$.exam.fullMarks").value(9.0))
                .andExpect(jsonPath("$.rows[0].marks").value(9.0));
        bonus(examA, 0).andExpect(status().isOk());
        fetch("/api/admin/exams/" + examA + "/leaderboard", adminToken)
                .andExpect(jsonPath("$.exam.fullMarks").value(4.0))
                .andExpect(jsonPath("$.rows[*].marks", contains(4.0, 1.75, 1.75, 0.0)));
    }

    @Test
    void noMarkEverExceedsFullMarks() throws Exception {
        bonus(examA, 100).andExpect(status().isOk());
        String res = fetch("/api/admin/exams/" + examA + "/leaderboard", adminToken)
                .andReturn().getResponse().getContentAsString();
        BigDecimal full = json.readTree(res).get("exam").get("fullMarks").decimalValue();
        json.readTree(res).get("rows").forEach(r -> assertTrue(r.get("marks").decimalValue().compareTo(full) <= 0));
        assertEquals(0, new BigDecimal("104").compareTo(full));
    }

    @Test
    void invalidBonusIsRejected() throws Exception {
        bonus(examA, 101).andExpect(status().isBadRequest());
        bonus(examA, -1).andExpect(status().isBadRequest());
        bonus(examA, 1.234).andExpect(status().isBadRequest());
        send(post("/api/admin/exams/" + examA + "/bonus"), adminToken, Map.of()).andExpect(status().isBadRequest());
        bonus(examC, 2).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NO_ATTEMPTS"));
        assertEquals(0, BigDecimal.ZERO.compareTo(exams.findById(examA).orElseThrow().getBonusMarks()));
    }

    // ---------- answer sheet ----------

    @Test
    void adminSeesAStudentsAnswerSheetWithCorrectWrongAndBlank() throws Exception {
        fetch("/api/admin/exams/" + examA + "/attempts/" + nilaId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.name").value("Nila"))
                .andExpect(jsonPath("$.student.className").value("Class 9"))
                .andExpect(jsonPath("$.score.marks").value(1.75))
                .andExpect(jsonPath("$.score.correct").value(2))
                .andExpect(jsonPath("$.score.wrong").value(1))
                .andExpect(jsonPath("$.score.blank").value(1))
                .andExpect(jsonPath("$.score.timeSec").value(200))
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.participants").value(4))
                .andExpect(jsonPath("$.review", hasSize(4)))
                .andExpect(jsonPath("$.review[0].chosenIdx").value(0))
                .andExpect(jsonPath("$.review[0].correctIdx").value(0))
                .andExpect(jsonPath("$.review[2].chosenIdx").value(0))
                .andExpect(jsonPath("$.review[2].correctIdx").value(2))
                .andExpect(jsonPath("$.review[3].chosenIdx").doesNotExist())
                .andExpect(jsonPath("$.review[0].options", hasSize(4)));
    }

    @Test
    void answerSheetShowsRawMarksBonusAndTotalSeparately() throws Exception {
        bonus(examA, 2).andExpect(status().isOk());
        fetch("/api/admin/exams/" + examA + "/attempts/" + nilaId, adminToken)
                .andExpect(jsonPath("$.score.rawMarks").value(1.75))
                .andExpect(jsonPath("$.score.bonus").value(2.0))
                .andExpect(jsonPath("$.score.marks").value(3.75));
    }

    @Test
    void answerSheetErrors() throws Exception {
        fetch("/api/admin/exams/" + examA + "/attempts/" + absentId, adminToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_PARTICIPATED"));
        fetch("/api/admin/exams/" + examA + "/attempts/" + adminId, adminToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        fetch("/api/admin/exams/" + examA + "/attempts/" + nilaId, betaAdminToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXAM_NOT_FOUND"));
    }

    // ---------- student's own result ----------

    @Test
    void studentSeesResultOnlyAfterPublishing() throws Exception {
        fetch("/api/student/exams/" + examA + "/result", rafiToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RESULT_NOT_PUBLISHED"));
        publish(examA);
        fetch("/api/student/exams/" + examA + "/result", rafiToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score.marks").value(4.0))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.participants").value(4))
                .andExpect(jsonPath("$.review", hasSize(4)))
                .andExpect(jsonPath("$.student").doesNotExist());
    }

    @Test
    void studentResultRulesForOutsiders() throws Exception {
        publish(examA);
        fetch("/api/student/exams/" + examA + "/result", absentToken)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_PARTICIPATED"));
        fetch("/api/student/exams/" + examA + "/result", tenToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_YOUR_CLASS"));
        fetch("/api/student/exams/" + examA + "/result", adminToken).andExpect(status().isForbidden());   // staff use /api/admin
    }

    @Test
    void studentLeaderboardMarksOnlyMeAndHidesUserIds() throws Exception {
        fetch("/api/student/exams/" + examA + "/leaderboard", rafiToken).andExpect(status().isForbidden());
        publish(examA);
        fetch("/api/student/exams/" + examA + "/leaderboard", nilaToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*].me", contains(false, true, false, false)))
                .andExpect(jsonPath("$.rows[0].userId").doesNotExist())
                .andExpect(jsonPath("$.rows[*].rank", contains(1, 2, 2, 4)));
        fetch("/api/student/exams/" + examA + "/leaderboard", tenToken).andExpect(status().isForbidden());
    }

    @Test
    void unpublishingHidesTheResultAgain() throws Exception {
        publish(examA);
        fetch("/api/student/exams/" + examA + "/result", rafiToken).andExpect(status().isOk());
        send(delete("/api/admin/exams/" + examA + "/publish"), adminToken, null).andExpect(status().isOk());
        fetch("/api/student/exams/" + examA + "/result", rafiToken).andExpect(status().isForbidden());
    }

    // ---------- overall leaderboard ----------

    @Test
    void overallBoardAddsUpPublishedExamsOnly() throws Exception {
        publish(examA);
        fetch("/api/student/leaderboard/overall", rafiToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("Class 9"))
                .andExpect(jsonPath("$.examCount").value(1))
                .andExpect(jsonPath("$.fullMarks").value(4.0))
                .andExpect(jsonPath("$.rows[0].name").value("Rafi"))
                .andExpect(jsonPath("$.rows[0].marks").value(4.0));   // exam B is not published, so it is not counted

        publish(examB);
        fetch("/api/student/leaderboard/overall", nilaToken)
                .andExpect(jsonPath("$.examCount").value(2))
                .andExpect(jsonPath("$.fullMarks").value(6.0))
                .andExpect(jsonPath("$.rows[*].name", contains("Rafi", "Nila", "Sara", "Tanvir")))
                .andExpect(jsonPath("$.rows[*].marks", contains(6.0, 2.5, 1.75, 0.0)))
                .andExpect(jsonPath("$.rows[*].timeSec", contains(150, 260, 200, 150)))
                .andExpect(jsonPath("$.rows[*].rank", contains(1, 2, 3, 4)))
                .andExpect(jsonPath("$.rows[*].taken", contains(2, 2, 1, 1)))
                .andExpect(jsonPath("$.rows[*].me", contains(false, true, false, false)));
    }

    @Test
    void overallBoardIsEmptyBeforeAnythingIsPublished() throws Exception {
        fetch("/api/student/leaderboard/overall", rafiToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.examCount").value(0)).andExpect(jsonPath("$.rows", hasSize(0)));
    }

    @Test
    void overallBoardFollowsABonus() throws Exception {
        publish(examA);
        bonus(examA, 1).andExpect(status().isOk());
        fetch("/api/student/leaderboard/overall", rafiToken)
                .andExpect(jsonPath("$.fullMarks").value(5.0))
                .andExpect(jsonPath("$.rows[0].marks").value(5.0));
    }

    // ---------- admin summary, section list, report ----------

    @Test
    void summaryListsPendingAndPublishedExams() throws Exception {
        fetch("/api/admin/summary", adminToken)
                .andExpect(jsonPath("$.students").value(6))
                .andExpect(jsonPath("$.examCount").value(3))
                .andExpect(jsonPath("$.pending", hasSize(2)))      // A and B have attempts; C has none
                .andExpect(jsonPath("$.published", hasSize(0)));
        publish(examA);
        fetch("/api/admin/summary", adminToken)
                .andExpect(jsonPath("$.pending", hasSize(1)))
                .andExpect(jsonPath("$.pending[0].title").value("Exam B"))
                .andExpect(jsonPath("$.published[0].title").value("Exam A"))
                .andExpect(jsonPath("$.published[0].attempts").value(4));
        publish(examB);
        fetch("/api/admin/summary", adminToken).andExpect(jsonPath("$.published[0].title").value("Exam B"));   // latest published first
    }

    @Test
    void sectionListShowsTakenCountAndTotalMarks() throws Exception {
        fetch("/api/admin/classes/" + nineId + "/sections/" + nineSectionId + "/students", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examCount").value(3))
                .andExpect(jsonPath("$.students[*].roll", contains("101", "102", "103", "104", "105")))
                .andExpect(jsonPath("$.students[*].taken", contains(2, 2, 1, 1, 0)))
                .andExpect(jsonPath("$.students[*].marks", contains(6.0, 2.5, 1.75, 0.0, 0.0)));
        fetch("/api/admin/classes/" + tenId + "/sections/" + nineSectionId + "/students", adminToken)
                .andExpect(status().isNotFound());   // that section is not in Class 10
        fetch("/api/admin/classes/" + nineId + "/sections/" + nineSectionId + "/students", betaAdminToken)
                .andExpect(status().isNotFound());
    }

    @Test
    void studentReportOrdersOpenThenTakenThenMissedAndTotalsTheTaken() throws Exception {
        fetch("/api/admin/students/" + nilaId + "/report", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.name").value("Nila"))
                .andExpect(jsonPath("$.exams[*].title", contains("Exam C", "Exam B", "Exam A")))   // open, then taken (latest first)
                .andExpect(jsonPath("$.exams[0].taken").value(false))
                .andExpect(jsonPath("$.exams[1].marks").value(0.75))
                .andExpect(jsonPath("$.exams[2].rank").value(2))
                .andExpect(jsonPath("$.summary.examCount").value(3))
                .andExpect(jsonPath("$.summary.taken").value(2))
                .andExpect(jsonPath("$.summary.missed").value(1))
                .andExpect(jsonPath("$.summary.marks").value(2.5))
                .andExpect(jsonPath("$.summary.full").value(6.0));

        publish(examA);   // absent never took A, and A is closed now -> "missed", listed last
        fetch("/api/admin/students/" + absentId + "/report", adminToken)
                .andExpect(jsonPath("$.exams[*].title", contains("Exam C", "Exam B", "Exam A")))
                .andExpect(jsonPath("$.summary.taken").value(0))
                .andExpect(jsonPath("$.summary.marks").value(0.0));
        fetch("/api/admin/students/" + nilaId + "/report", betaAdminToken).andExpect(status().isNotFound());
        fetch("/api/admin/students/" + adminId + "/report", adminToken).andExpect(status().isNotFound());
    }

    @Test
    void anotherInstitutionCannotTouchMyResults() throws Exception {
        send(post("/api/admin/exams/" + examA + "/publish"), betaAdminToken, null).andExpect(status().isNotFound());
        bonus(examA, 1);   // sent with the alpha admin; now try the same as beta:
        send(post("/api/admin/exams/" + examA + "/bonus"), betaAdminToken, Map.of("bonus", 3)).andExpect(status().isNotFound());
        fetch("/api/admin/exams/" + examA + "/leaderboard", betaAdminToken).andExpect(status().isNotFound());
        assertEquals(0, new BigDecimal("1.00").compareTo(exams.findById(examA).orElseThrow().getBonusMarks()), "only my own bonus is set");
        assertEquals(alphaId, exams.findById(examA).orElseThrow().getInstitutionId());
    }
}

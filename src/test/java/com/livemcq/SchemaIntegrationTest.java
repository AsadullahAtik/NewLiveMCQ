package com.livemcq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livemcq.domain.AppUser;
import com.livemcq.domain.Attempt;
import com.livemcq.domain.AttemptAnswer;
import com.livemcq.domain.AttemptRank;
import com.livemcq.domain.Exam;
import com.livemcq.domain.Institution;
import com.livemcq.domain.OverallRank;
import com.livemcq.domain.Role;
import com.livemcq.domain.SchoolClass;
import com.livemcq.domain.Section;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.AttemptAnswerRepository;
import com.livemcq.repo.AttemptRankRepository;
import com.livemcq.repo.AttemptRepository;
import com.livemcq.repo.ExamRepository;
import com.livemcq.repo.InstitutionRepository;
import com.livemcq.repo.OverallRankRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.repo.SectionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots only the JPA layer against a REAL PostgreSQL database (see application-test.yml):
 *  - Flyway applies V1, then Hibernate's ddl-auto=validate checks every entity against the tables
 *    (so a typo in a column name fails here, not in production);
 *  - one exam is saved through the entities and the attempt_ranks view is read back.
 * @DataJpaTest rolls each test back, so the database stays empty.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)   // use PostgreSQL, not in-memory H2
@ActiveProfiles("test")
class SchemaIntegrationTest {

    @Autowired InstitutionRepository institutions;
    @Autowired SchoolClassRepository classes;
    @Autowired SectionRepository sections;
    @Autowired AppUserRepository users;
    @Autowired ExamRepository exams;
    @Autowired AttemptRepository attempts;
    @Autowired AttemptAnswerRepository answers;
    @Autowired AttemptRankRepository ranks;
    @Autowired OverallRankRepository overall;
    @Autowired EntityManager em;

    private static final List<String> OPTS = List.of("a", "b", "c", "d");

    @Test
    void contextStartsAndSchemaMatchesEntities() {
        // reaching this line means Flyway migrated and Hibernate validated all entities
        assertTrue(institutions.findBySlug("nope").isEmpty());
    }

    @Test
    void savedAttemptIsScoredByTheViewWithBonusAndTiedRanks() {
        Institution inst = institutions.save(new Institution("Alpha Coaching", "alpha"));
        SchoolClass cls = classes.save(new SchoolClass(inst.getId(), "Class 9"));
        Section sec = sections.save(new Section(cls, "A"));
        AppUser rafi = users.save(new AppUser(inst.getId(), Role.STUDENT, "Rafi Ahmed", "101", "x", sec));
        AppUser tanzila = users.save(new AppUser(inst.getId(), Role.STUDENT, "Tanzila Islam", "102", "x", sec));
        AppUser mehedi = users.save(new AppUser(inst.getId(), Role.STUDENT, "Mehedi Hasan", "103", "x", sec));

        Exam exam = new Exam(cls, "General Science", 5, new BigDecimal("1"), new BigDecimal("0.25"));
        exam.addQuestion("Q1", 0, OPTS);
        exam.addQuestion("Q2", 1, OPTS);
        exam.addQuestion("Q3", 2, OPTS);
        exam = exams.save(exam);

        // Rafi: all three right in 100s. Tanzila: all right but slower (200s). Mehedi: 1 right, 1 wrong, 1 blank.
        submit(exam, rafi, 100, 0, 1, 2);
        submit(exam, tanzila, 200, 0, 1, 2);
        submit(exam, mehedi, 150, 0, 3, null);
        em.flush();
        em.clear();

        List<AttemptRank> board = ranks.findByExamIdOrderByRankNoAscTimeSecAsc(exam.getId());
        assertEquals(3, board.size());
        assertEquals(rafi.getId(), board.get(0).getUserId());
        assertEquals(1, board.get(0).getRankNo());
        assertEquals(0, new BigDecimal("3").compareTo(board.get(0).getMarks()));
        assertEquals(2, board.get(1).getRankNo());                                // same marks, more time -> 2nd
        AttemptRank m = board.get(2);
        assertEquals(3, m.getRankNo());
        assertEquals(1, m.getCorrect());
        assertEquals(1, m.getWrong());
        assertEquals(1, m.getBlank());
        assertEquals(0, new BigDecimal("0.75").compareTo(m.getMarks()));          // 1 - 0.25

        // teacher gives everyone +1.5: marks and full marks both rise, nobody exceeds full marks
        Exam reloaded = exams.findById(exam.getId()).orElseThrow();
        reloaded.setBonusMarks(new BigDecimal("1.5"));
        exams.saveAndFlush(reloaded);
        em.clear();

        List<AttemptRank> after = ranks.findByExamIdOrderByRankNoAscTimeSecAsc(exam.getId());
        assertEquals(0, new BigDecimal("4.50").compareTo(after.get(0).getMarks()));
        assertEquals(0, new BigDecimal("4.50").compareTo(after.get(0).getFullMarks()));
        assertTrue(after.stream().allMatch(r -> r.getMarks().compareTo(r.getFullMarks()) <= 0));
        assertEquals(0, new BigDecimal("4.50").compareTo(exams.findById(exam.getId()).orElseThrow().fullMarks()));
    }

    @Test
    void overallLeaderboardCountsOnlyPublishedExamsOfTheCurrentClass() {
        Institution inst = institutions.save(new Institution("Beta School", "beta"));
        SchoolClass nine = classes.save(new SchoolClass(inst.getId(), "Class 9"));
        SchoolClass ten = classes.save(new SchoolClass(inst.getId(), "Class 10"));
        Section nineA = sections.save(new Section(nine, "A"));
        Section tenA = sections.save(new Section(ten, "A"));
        AppUser asha = users.save(new AppUser(inst.getId(), Role.STUDENT, "Asha Rahman", "201", "x", nineA));
        AppUser babu = users.save(new AppUser(inst.getId(), Role.STUDENT, "Babu Mia", "202", "x", nineA));
        AppUser moved = users.save(new AppUser(inst.getId(), Role.STUDENT, "Moved Student", "203", "x", nineA));

        Exam published = exams.save(oneQuestionExam(nine, "Published exam"));
        Exam draft = exams.save(oneQuestionExam(nine, "Not published yet"));
        submit(published, asha, 60, 0);   // right
        submit(published, babu, 60, 1);   // wrong: 0 marks
        submit(published, moved, 30, 0);
        submit(draft, asha, 10, 0);       // must NOT count: that exam is not published
        published.publish();
        exams.save(published);
        moved.moveTo(tenA);               // moved to Class 10 after taking a Class 9 exam
        users.save(moved);
        em.flush();
        em.clear();

        List<OverallRank> board = overall.findByClassIdOrderByRankNoAscTotalTimeSecAsc(nine.getId());
        assertEquals(2, board.size());                                      // the moved student is not on Class 9's board
        assertEquals(asha.getId(), board.get(0).getUserId());
        assertEquals(1, board.get(0).getRankNo());
        assertEquals(1, board.get(0).getExamsTaken());                      // the unpublished exam added nothing
        assertEquals(0, new BigDecimal("1").compareTo(board.get(0).getTotalMarks()));
        assertEquals(0, new BigDecimal("1").compareTo(board.get(0).getFullMarks()));
        assertEquals(2, board.get(1).getRankNo());
        assertTrue(overall.findByClassIdOrderByRankNoAscTotalTimeSecAsc(ten.getId()).isEmpty());   // Class 10 has no published exam
    }

    private static Exam oneQuestionExam(SchoolClass cls, String title) {
        Exam e = new Exam(cls, title, 5, new BigDecimal("1"), new BigDecimal("0.25"));
        e.addQuestion("Q1", 0, OPTS);
        return e;
    }

    /** Saves an attempt plus one answer row per question. A null choice is stored as "left blank". */
    private void submit(Exam exam, AppUser student, int timeSec, Integer... picks) {
        Attempt attempt = attempts.save(new Attempt(exam, student, timeSec));
        for (int i = 0; i < exam.getQuestions().size(); i++) {
            Long questionId = exam.getQuestions().get(i).getId();
            answers.save(new AttemptAnswer(attempt.getId(), questionId, exam.getId(), picks[i]));
        }
    }
}

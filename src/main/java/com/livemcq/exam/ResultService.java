package com.livemcq.exam;

import com.livemcq.domain.AppUser;
import com.livemcq.domain.Attempt;
import com.livemcq.domain.AttemptAnswer;
import com.livemcq.domain.AttemptRank;
import com.livemcq.domain.Exam;
import com.livemcq.domain.OverallRank;
import com.livemcq.domain.Question;
import com.livemcq.domain.Role;
import com.livemcq.domain.SchoolClass;
import com.livemcq.domain.Section;
import com.livemcq.exam.ExamDtos.ExamSummary;
import com.livemcq.exam.ResultDtos.*;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.AttemptAnswerRepository;
import com.livemcq.repo.AttemptRankRepository;
import com.livemcq.repo.AttemptRepository;
import com.livemcq.repo.ExamRepository;
import com.livemcq.repo.OverallRankRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.repo.SectionRepository;
import com.livemcq.security.AuthPrincipal;
import com.livemcq.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Results, ranks and leaderboards. Marks and ranks are NEVER computed here: they are read from the
 * database views (attempt_ranks, overall_ranks), which the HTML parity test has already checked.
 */
@Service
@Transactional(readOnly = true)
public class ResultService {

    private static final Comparator<AttemptRank> BOARD_ORDER = Comparator
            .comparingInt(AttemptRank::getRankNo).thenComparingInt(AttemptRank::getTimeSec)
            .thenComparing(AttemptRank::getUserId);

    private final ExamRepository exams;
    private final AttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final AttemptRankRepository ranks;
    private final OverallRankRepository overall;
    private final AppUserRepository users;
    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final ExamAdminService examAdmin;
    private final Clock clock;

    public ResultService(ExamRepository exams, AttemptRepository attempts, AttemptAnswerRepository answers,
                         AttemptRankRepository ranks, OverallRankRepository overall, AppUserRepository users,
                         SchoolClassRepository classes, SectionRepository sections, ExamAdminService examAdmin,
                         Clock clock) {
        this.exams = exams;
        this.attempts = attempts;
        this.answers = answers;
        this.ranks = ranks;
        this.overall = overall;
        this.users = users;
        this.classes = classes;
        this.sections = sections;
        this.examAdmin = examAdmin;
        this.clock = clock;
    }

    // =====================================================================================
    // admin: publish, bonus
    // =====================================================================================

    @Transactional
    public PublishResult publish(long institutionId, long examId) {
        Exam exam = exam(institutionId, examId);
        if (attempts.countByExamId(exam.getId()) == 0) {
            throw ApiException.unprocessable("NO_ATTEMPTS", "Nobody has submitted yet, so results cannot be published");
        }
        if (!exam.isPublished()) {
            exam.publish(clock.instant());
        }
        exams.flush();
        return new PublishResult(true);
    }

    @Transactional
    public PublishResult unpublish(long institutionId, long examId) {
        exam(institutionId, examId).unpublish();
        exams.flush();
        return new PublishResult(false);
    }

    /**
     * One flat bonus for every participant. Nothing is rewritten: marks are derived from the exam's
     * bonus in the database view, so every score, rank and full mark changes at once, and a mark
     * can never exceed the (also raised) full marks. A new bonus REPLACES the old one; 0 removes it.
     */
    @Transactional
    public BonusResult setBonus(long institutionId, long examId, BigDecimal bonus) {
        Exam exam = exam(institutionId, examId);
        int participants = (int) attempts.countByExamId(exam.getId());
        if (participants == 0) {
            throw ApiException.unprocessable("NO_ATTEMPTS", "Nobody took this exam, so a bonus cannot be added");
        }
        exam.setBonusMarks(bonus.setScale(2, RoundingMode.HALF_UP));
        exams.flush();   // write it NOW: a later read of the database views must see the new bonus
        return new BonusResult(exam.getBonusMarks(), participants, exam.fullMarks());
    }

    // =====================================================================================
    // admin: reading
    // =====================================================================================

    public AdminSummary summary(long institutionId) {
        List<ExamSummary> all = examAdmin.list(institutionId);
        List<ExamSummary> pending = all.stream().filter(e -> !e.published() && e.attempts() > 0).toList();
        List<ExamSummary> published = all.stream().filter(ExamSummary::published)
                .sorted(Comparator.comparing(ExamSummary::publishedAt).reversed().thenComparing(Comparator.comparingLong(ExamSummary::id).reversed()))
                .toList();
        return new AdminSummary(users.countByInstitutionIdAndRole(institutionId, Role.STUDENT), all.size(), pending, published);
    }

    public Leaderboard adminLeaderboard(long institutionId, long examId) {
        Exam exam = exam(institutionId, examId);
        return leaderboard(exam, null, true);
    }

    public AnswerSheet answerSheet(long institutionId, long examId, long studentId) {
        Exam exam = exam(institutionId, examId);
        AppUser student = users.findByIdAndInstitutionId(studentId, institutionId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.notFound("STUDENT_NOT_FOUND", "Student not found"));
        AttemptRank r = ranks.findByExamIdAndUserId(exam.getId(), student.getId())
                .orElseThrow(() -> ApiException.notFound("NOT_PARTICIPATED", "This student did not take this exam"));
        return new AnswerSheet(info(exam), ref(student), score(r), r.getRankNo(), r.getParticipants(), review(exam, r.getAttemptId()));
    }

    /** Students of one section with how many of the class's exams they took and their total marks. */
    public SectionStudents sectionStudents(long institutionId, long classId, long sectionId) {
        SchoolClass c = classes.findByIdAndInstitutionId(classId, institutionId)
                .orElseThrow(() -> ApiException.notFound("CLASS_NOT_FOUND", "Class or section not found"));
        Section s = sections.findByIdAndInstitutionId(sectionId, institutionId)
                .filter(sec -> sec.getSchoolClass().getId().equals(c.getId()))
                .orElseThrow(() -> ApiException.notFound("CLASS_NOT_FOUND", "Class or section not found"));
        List<Exam> classExams = exams.findBySchoolClassIdOrderByCreatedAtDescIdDesc(c.getId());
        Set<Long> examIds = classExams.stream().map(Exam::getId).collect(Collectors.toSet());

        List<SectionStudentRow> rows = users.findBySectionId(s.getId()).stream()
                .filter(u -> u.getRole() == Role.STUDENT)
                .sorted(Comparator.comparingInt((AppUser u) -> u.getRoll().length()).thenComparing(AppUser::getRoll))
                .map(u -> {
                    List<AttemptRank> mine = ranks.findByUserId(u.getId()).stream()
                            .filter(r -> examIds.contains(r.getExamId())).toList();
                    return new SectionStudentRow(u.getId(), u.getName(), u.getRoll(), u.getPhone(), mine.size(),
                            classExams.size(), sum(mine.stream().map(AttemptRank::getMarks)));
                }).toList();
        return new SectionStudents(c.getId(), c.getName(), s.getId(), s.getName(), classExams.size(), rows);
    }

    /** Every exam of the student's class: open first, then taken (latest first), then missed. */
    public StudentReport report(long institutionId, long studentId) {
        AppUser student = users.findByIdAndInstitutionId(studentId, institutionId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.notFound("STUDENT_NOT_FOUND", "Student not found"));
        List<Exam> classExams = exams.findBySchoolClassIdOrderByCreatedAtDescIdDesc(student.getSection().getSchoolClass().getId());

        record Row(ReportRow row, int group, java.time.Instant at) { }
        List<Row> rows = new ArrayList<>();
        for (Exam e : classExams) {
            AttemptRank r = ranks.findByExamIdAndUserId(e.getId(), student.getId()).orElse(null);
            if (r == null) {
                rows.add(new Row(new ReportRow(e.getId(), e.getTitle(), e.fullMarks(), e.isPublished(), false,
                        e.getBonusMarks(), null, null, null, null, null, null, null), e.isPublished() ? 2 : 0, null));
            } else {
                Attempt a = attempts.findByExamIdAndUserId(e.getId(), student.getId()).orElseThrow();
                rows.add(new Row(new ReportRow(e.getId(), e.getTitle(), r.getFullMarks(), e.isPublished(), true,
                        r.getBonusMarks(), r.getMarks(), r.getCorrect(), r.getWrong(), r.getBlank(), r.getTimeSec(),
                        r.getRankNo(), r.getParticipants()), 1, a.getSubmittedAt()));
            }
        }
        // stable sort: equal items keep the newest-first order they came in with
        List<Row> sorted = rows.stream().sorted(Comparator.comparingInt(Row::group)
                .thenComparing(x -> x.group() == 1 ? x.at() : null, Comparator.nullsFirst(Comparator.<java.time.Instant>reverseOrder()))).toList();
        List<ReportRow> out = sorted.stream().map(Row::row).toList();
        List<ReportRow> taken = out.stream().filter(ReportRow::taken).toList();
        ReportSummary summary = new ReportSummary(out.size(), taken.size(), out.size() - taken.size(),
                sum(taken.stream().map(ReportRow::marks)), sum(taken.stream().map(ReportRow::fullMarks)));
        return new StudentReport(ref(student), out, summary);
    }

    // =====================================================================================
    // student
    // =====================================================================================

    public MyResult myResult(AuthPrincipal p, long examId) {
        AppUser me = student(p);
        Exam exam = examOfMyClass(me, examId);
        AttemptRank r = ranks.findByExamIdAndUserId(exam.getId(), me.getId())
                .orElseThrow(() -> ApiException.notFound("NOT_PARTICIPATED", "You did not take this exam"));
        requirePublished(exam);
        return new MyResult(info(exam), score(r), r.getRankNo(), r.getParticipants(), review(exam, r.getAttemptId()));
    }

    public Leaderboard myLeaderboard(AuthPrincipal p, long examId) {
        AppUser me = student(p);
        Exam exam = examOfMyClass(me, examId);
        requirePublished(exam);
        return leaderboard(exam, me.getId(), false);
    }

    public OverallBoard overallBoard(AuthPrincipal p) {
        AppUser me = student(p);
        SchoolClass c = me.getSection().getSchoolClass();
        List<Exam> published = exams.findBySchoolClassIdOrderByCreatedAtDescIdDesc(c.getId()).stream()
                .filter(Exam::isPublished).toList();
        List<OverallRank> list = overall.findByClassIdOrderByRankNoAscTotalTimeSecAsc(c.getId());
        Map<Long, AppUser> byId = usersById(list.stream().map(OverallRank::getUserId).toList());
        List<OverallRow> rows = list.stream()
                .sorted(Comparator.comparingInt(OverallRank::getRankNo).thenComparingInt(OverallRank::getTotalTimeSec)
                        .thenComparing(OverallRank::getUserId))
                .map(o -> new OverallRow(o.getRankNo(), byId.get(o.getUserId()).getName(), byId.get(o.getUserId()).getRoll(),
                        o.getTotalMarks(), o.getTotalTimeSec(), o.getExamsTaken(), o.getUserId().equals(me.getId())))
                .toList();
        return new OverallBoard(c.getName(), published.size(),
                sum(published.stream().map(Exam::fullMarks)), rows);
    }

    // =====================================================================================
    // helpers
    // =====================================================================================

    private Leaderboard leaderboard(Exam exam, Long meId, boolean forAdmin) {
        List<AttemptRank> list = ranks.findByExamIdOrderByRankNoAscTimeSecAsc(exam.getId()).stream().sorted(BOARD_ORDER).toList();
        Map<Long, AppUser> byId = usersById(list.stream().map(AttemptRank::getUserId).toList());
        List<BoardRow> rows = list.stream().map(r -> new BoardRow(r.getRankNo(), forAdmin ? r.getUserId() : null,
                byId.get(r.getUserId()).getName(), byId.get(r.getUserId()).getRoll(), r.getMarks(),
                r.getCorrect(), r.getWrong(), r.getBlank(), r.getTimeSec(), r.getUserId().equals(meId))).toList();
        return new Leaderboard(info(exam), list.size(), rows);
    }

    private Map<Long, AppUser> usersById(List<Long> ids) {
        Map<Long, AppUser> map = new HashMap<>();
        users.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private List<ReviewItem> review(Exam exam, long attemptId) {
        Map<Long, Integer> chosen = new HashMap<>();
        for (AttemptAnswer a : answers.findByIdAttemptId(attemptId)) {
            chosen.put(a.getId().getQuestionId(), a.getChosenIdx());
        }
        List<ReviewItem> items = new ArrayList<>();
        for (Question q : exam.getQuestions()) {
            items.add(new ReviewItem(q.getBody(), List.copyOf(q.getOptions()), q.getCorrectIdx(), chosen.get(q.getId())));
        }
        return items;
    }

    private static Score score(AttemptRank r) {
        return new Score(r.getMarks(), r.getRawMarks(), r.getBonusMarks(), r.getCorrect(), r.getWrong(), r.getBlank(), r.getTimeSec());
    }

    private ExamInfo info(Exam e) {
        return new ExamInfo(e.getId(), e.getTitle(), e.getSchoolClass().getName(), e.getMarksPerQuestion(),
                e.getNegativeMarks(), e.getBonusMarks(), e.fullMarks(), e.getQuestions().size(), e.isPublished());
    }

    private static StudentRef ref(AppUser u) {
        Section s = u.getSection();
        return new StudentRef(u.getId(), u.getName(), u.getRoll(),
                s == null ? null : s.getSchoolClass().getName(), s == null ? null : s.getName());
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private Exam exam(long institutionId, long examId) {
        return exams.findByIdAndInstitutionId(examId, institutionId)
                .orElseThrow(() -> ApiException.notFound("EXAM_NOT_FOUND", "Exam not found"));
    }

    private AppUser student(AuthPrincipal p) {
        return users.findByIdAndInstitutionId(p.userId(), p.institutionId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "This account no longer exists"));
    }

    private Exam examOfMyClass(AppUser me, long examId) {
        Exam exam = exam(me.getInstitutionId(), examId);
        if (!exam.getSchoolClass().getId().equals(me.getSection().getSchoolClass().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_CLASS", "This exam is not for your class");
        }
        return exam;
    }

    private static void requirePublished(Exam exam) {
        if (!exam.isPublished()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RESULT_NOT_PUBLISHED", "Results have not been published yet");
        }
    }
}

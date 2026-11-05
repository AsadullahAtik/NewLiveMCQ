package com.livemcq.exam;

import com.livemcq.domain.AppUser;
import com.livemcq.domain.Attempt;
import com.livemcq.domain.AttemptAnswer;
import com.livemcq.domain.Exam;
import com.livemcq.domain.ExamSession;
import com.livemcq.domain.Question;
import com.livemcq.exam.StudentExamDtos.PaperQuestion;
import com.livemcq.exam.StudentExamDtos.PaperView;
import com.livemcq.exam.StudentExamDtos.Status;
import com.livemcq.exam.StudentExamDtos.StudentExamView;
import com.livemcq.exam.StudentExamDtos.SubmitResult;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.AttemptAnswerRepository;
import com.livemcq.repo.AttemptRepository;
import com.livemcq.repo.ExamRepository;
import com.livemcq.repo.ExamSessionRepository;
import com.livemcq.security.AuthPrincipal;
import com.livemcq.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The student's side of an exam: see the list, start (the SERVER starts the clock), submit.
 * The client's clock and the client's "time taken" are never trusted.
 */
@Service
@Transactional(readOnly = true)
public class StudentExamService {

    /** Network delay allowance: a submit this long after the deadline is still accepted. */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(15);

    private final AppUserRepository users;
    private final ExamRepository exams;
    private final ExamSessionRepository sessions;
    private final AttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final Clock clock;

    public StudentExamService(AppUserRepository users, ExamRepository exams, ExamSessionRepository sessions,
                              AttemptRepository attempts, AttemptAnswerRepository answers, Clock clock) {
        this.users = users;
        this.exams = exams;
        this.sessions = sessions;
        this.attempts = attempts;
        this.answers = answers;
        this.clock = clock;
    }

    public List<StudentExamView> list(AuthPrincipal p) {
        AppUser me = student(p);
        Instant now = clock.instant();
        return exams.findBySchoolClassIdOrderByCreatedAtDescIdDesc(classIdOf(me)).stream()
                .map(e -> toView(e, me, now))
                .toList();
    }

    /** Starts the exam, or resumes it with the REMAINING time if it was already started. */
    @Transactional
    public PaperView start(AuthPrincipal p, long examId) {
        AppUser me = student(p);
        Exam exam = examOfMyClass(me, examId);
        requireOpen(exam, me);
        Instant now = clock.instant();

        ExamSession session = sessions.findByExamIdAndUserId(exam.getId(), me.getId()).orElse(null);
        if (session == null) {
            try {
                session = sessions.saveAndFlush(new ExamSession(exam, me, now));
            } catch (DataIntegrityViolationException e) {   // double tap / two devices at the same instant
                throw ApiException.conflict("TRY_AGAIN", "The exam is already starting; please try again");
            }
        } else if (!now.isBefore(session.getDeadlineAt())) {
            throw ApiException.conflict("TIME_EXPIRED", "Your time for this exam has ended");
        }
        return toPaper(exam, session, now);
    }

    @Transactional
    public SubmitResult submit(AuthPrincipal p, long examId, List<Integer> given) {
        AppUser me = student(p);
        Exam exam = examOfMyClass(me, examId);
        requireOpen(exam, me);
        ExamSession session = sessions.findByExamIdAndUserId(exam.getId(), me.getId())
                .orElseThrow(() -> ApiException.conflict("NOT_STARTED", "Start the exam before submitting"));
        Instant now = clock.instant();
        if (now.isAfter(session.getDeadlineAt().plus(SUBMIT_GRACE))) {
            throw ApiException.conflict("TIME_EXPIRED", "Your time for this exam has ended");
        }

        List<Question> questions = exam.getQuestions();
        if (given.size() != questions.size()) {
            throw ApiException.unprocessable("ANSWER_COUNT", "Send exactly one answer (or null) per question");
        }
        for (Integer a : given) {
            if (a != null && (a < 0 || a > 3)) {
                throw ApiException.unprocessable("ANSWER_INVALID", "Each answer must be null or a number from 0 to 3");
            }
        }

        // time taken is measured by the server, and can never exceed the exam duration
        long elapsed = Duration.between(session.getStartedAt(), now).getSeconds();
        int timeSec = (int) Math.max(1, Math.min(elapsed, exam.getDurationMinutes() * 60L));

        Attempt attempt;
        try {
            attempt = attempts.saveAndFlush(new Attempt(exam, me, timeSec));   // UNIQUE(exam,user) is the last line of defence
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_SUBMITTED", "You have already submitted this exam");
        }
        List<AttemptAnswer> rows = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            if (given.get(i) != null) {   // a blank answer is simply "no row"
                rows.add(new AttemptAnswer(attempt.getId(), questions.get(i).getId(), exam.getId(), given.get(i)));
            }
        }
        answers.saveAllAndFlush(rows);   // flush now so the ranking views see the answers in this same transaction
        return new SubmitResult(true, timeSec);
    }

    // ---- helpers ----

    private AppUser student(AuthPrincipal p) {
        return users.findByIdAndInstitutionId(p.userId(), p.institutionId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "This account no longer exists"));
    }

    private static long classIdOf(AppUser student) {
        return student.getSection().getSchoolClass().getId();
    }

    /** 404 for another institution's exam (as if it did not exist), 403 for another class's exam. */
    private Exam examOfMyClass(AppUser me, long examId) {
        Exam exam = exams.findByIdAndInstitutionId(examId, me.getInstitutionId())
                .orElseThrow(() -> ApiException.notFound("EXAM_NOT_FOUND", "Exam not found"));
        if (!exam.getSchoolClass().getId().equals(classIdOf(me))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_CLASS", "This exam is not for your class");
        }
        return exam;
    }

    private void requireOpen(Exam exam, AppUser me) {
        if (exam.isPublished()) {
            throw ApiException.conflict("EXAM_CLOSED", "Results are published; this exam is closed");
        }
        if (attempts.existsByExamIdAndUserId(exam.getId(), me.getId())) {
            throw ApiException.conflict("ALREADY_SUBMITTED", "You have already submitted this exam");
        }
    }

    private StudentExamView toView(Exam e, AppUser me, Instant now) {
        Optional<ExamSession> session = sessions.findByExamIdAndUserId(e.getId(), me.getId());
        Status status;
        Long remaining = null;
        if (attempts.existsByExamIdAndUserId(e.getId(), me.getId())) {
            status = Status.SUBMITTED;
        } else if (e.isPublished()) {
            status = Status.MISSED;
        } else if (session.isPresent()) {
            boolean running = now.isBefore(session.get().getDeadlineAt());
            status = running ? Status.IN_PROGRESS : Status.EXPIRED;
            if (running) remaining = Duration.between(now, session.get().getDeadlineAt()).getSeconds();
        } else {
            status = Status.AVAILABLE;
        }
        return new StudentExamView(e.getId(), e.getTitle(), e.getDurationMinutes(), e.getMarksPerQuestion(),
                e.getNegativeMarks(), e.getQuestions().size(), e.fullMarks(), e.isPublished(), status, remaining);
    }

    private PaperView toPaper(Exam e, ExamSession s, Instant now) {
        List<PaperQuestion> qs = e.getQuestions().stream()
                .map(q -> new PaperQuestion(q.getBody(), List.copyOf(q.getOptions())))   // NO correctIdx here
                .toList();
        long remaining = Math.max(0, Duration.between(now, s.getDeadlineAt()).getSeconds());
        return new PaperView(e.getId(), e.getTitle(), e.getDurationMinutes(), e.getMarksPerQuestion(),
                e.getNegativeMarks(), e.fullMarks(), s.getStartedAt(), s.getDeadlineAt(), now, remaining, qs);
    }
}

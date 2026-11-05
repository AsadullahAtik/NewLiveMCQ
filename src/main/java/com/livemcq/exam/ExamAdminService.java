package com.livemcq.exam;

import com.livemcq.domain.Exam;
import com.livemcq.domain.Question;
import com.livemcq.domain.SchoolClass;
import com.livemcq.exam.ExamDtos.CreateExamRequest;
import com.livemcq.exam.ExamDtos.ExamDetail;
import com.livemcq.exam.ExamDtos.ExamSummary;
import com.livemcq.exam.ExamDtos.QuestionInput;
import com.livemcq.exam.ExamDtos.QuestionView;
import com.livemcq.repo.AttemptRepository;
import com.livemcq.repo.ExamRepository;
import com.livemcq.repo.ExamSessionRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.web.ApiException;
import com.livemcq.web.Text;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExamAdminService {

    private final ExamRepository exams;
    private final SchoolClassRepository classes;
    private final AttemptRepository attempts;
    private final ExamSessionRepository sessions;

    public ExamAdminService(ExamRepository exams, SchoolClassRepository classes, AttemptRepository attempts,
                            ExamSessionRepository sessions) {
        this.sessions = sessions;
        this.exams = exams;
        this.classes = classes;
        this.attempts = attempts;
    }

    @Transactional   // all-or-nothing: the exam and ALL its questions are saved together, or nothing is
    public ExamDetail create(long institutionId, CreateExamRequest r) {
        SchoolClass schoolClass = classes.findByIdAndInstitutionId(r.classId(), institutionId)
                .orElseThrow(() -> ApiException.unprocessable("CLASS_INVALID", "Choose a valid class"));
        String title = Text.trim(r.title());
        if (title.length() < 3) {
            throw ApiException.unprocessable("TITLE_TOO_SHORT", "Exam title needs at least 3 characters");
        }
        Exam exam = new Exam(schoolClass, title, r.durationMinutes(),
                r.marksPerQuestion().setScale(2, RoundingMode.HALF_UP),
                r.negativeMarks().setScale(2, RoundingMode.HALF_UP));
        addQuestions(exam, r.questions());
        return toDetail(exams.saveAndFlush(exam));
    }

    public List<ExamSummary> list(long institutionId) {
        return exams.findByInstitutionIdOrderByCreatedAtDescIdDesc(institutionId).stream().map(this::toSummary).toList();
    }

    public ExamDetail get(long institutionId, long examId) {
        return toDetail(find(institutionId, examId));
    }

    /**
     * Replaces every question. Allowed only while nobody has answered and results are not published:
     * answers point at question rows, so changing questions later would silently destroy submitted answers.
     */
    @Transactional
    public ExamDetail replaceQuestions(long institutionId, long examId, List<QuestionInput> inputs) {
        Exam exam = find(institutionId, examId);
        if (exam.isPublished()) {
            throw ApiException.conflict("EXAM_PUBLISHED", "Questions cannot be edited after results are published");
        }
        if (attempts.countByExamId(exam.getId()) > 0) {
            throw ApiException.conflict("EXAM_HAS_ATTEMPTS", "Questions cannot be edited after students have submitted");
        }
        if (sessions.countByExamId(exam.getId()) > 0) {   // someone is mid-exam: the paper must not change under them
            throw ApiException.conflict("EXAM_STARTED", "Questions cannot be edited after a student has started the exam");
        }
        exam.getQuestions().clear();
        exams.flush();   // run the DELETEs now: the new rows reuse sort_order 0,1,2.. (unique per exam)
        addQuestions(exam, inputs);
        return toDetail(exams.saveAndFlush(exam));
    }

    /** Deletes the exam; its questions go with it (JPA cascade) and its attempts too (database ON DELETE CASCADE). */
    @Transactional
    public void delete(long institutionId, long examId) {
        exams.delete(find(institutionId, examId));
    }

    // ---- helpers ----

    private Exam find(long institutionId, long examId) {
        return exams.findByIdAndInstitutionId(examId, institutionId)
                .orElseThrow(() -> ApiException.notFound("EXAM_NOT_FOUND", "Exam not found"));
    }

    private static void addQuestions(Exam exam, List<QuestionInput> inputs) {
        for (QuestionInput q : inputs) {
            exam.addQuestion(Text.trim(q.body()), q.correctIdx(), q.options().stream().map(Text::trim).toList());
        }
    }

    private ExamSummary toSummary(Exam e) {
        return new ExamSummary(e.getId(), e.getTitle(), e.getSchoolClass().getId(), e.getSchoolClass().getName(),
                e.getDurationMinutes(), e.getMarksPerQuestion(), e.getNegativeMarks(), e.getBonusMarks(),
                e.getQuestions().size(), e.baseMarks(), e.fullMarks(), e.isPublished(), e.getPublishedAt(),
                attempts.countByExamId(e.getId()));
    }

    private ExamDetail toDetail(Exam e) {
        List<QuestionView> qs = e.getQuestions().stream()
                .map((Question q) -> new QuestionView(q.getId(), q.getBody(), List.copyOf(q.getOptions()), q.getCorrectIdx()))
                .toList();
        return new ExamDetail(toSummary(e), qs);
    }
}

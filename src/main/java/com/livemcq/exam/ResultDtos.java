package com.livemcq.exam;

import org.springframework.lang.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.livemcq.exam.ExamDtos.ExamSummary;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** Shapes for results, leaderboards, reports and the bonus. */
public final class ResultDtos {

    private ResultDtos() { }

    public record ExamInfo(long id, String title, String className, BigDecimal marksPerQuestion,
                           BigDecimal negativeMarks, BigDecimal bonusMarks, BigDecimal fullMarks,
                           int questionCount, boolean published) { }

    /** rawMarks = before bonus; marks = rawMarks + bonus (what the leaderboard uses). */
    public record Score(BigDecimal marks, BigDecimal rawMarks, BigDecimal bonus,
                        int correct, int wrong, int blank, int timeSec) { }

    /** chosenIdx is null when the question was left blank. */
    public record ReviewItem(String body, List<String> options, int correctIdx, @Nullable Integer chosenIdx) { }

    public record StudentRef(long id, String name, String roll, @Nullable String className, @Nullable String sectionName) { }

    /** The admin's view of one student's paper. */
    public record AnswerSheet(ExamInfo exam, StudentRef student, Score score, int rank, int participants,
                              List<ReviewItem> review) { }

    /** The student's own result (same content, without the student block). */
    public record MyResult(ExamInfo exam, Score score, int rank, int participants, List<ReviewItem> review) { }

    /** userId is only sent to admins (it is null, and omitted, in the student's view). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BoardRow(int rank, @Nullable Long userId, String name, String roll, BigDecimal marks,
                           int correct, int wrong, int blank, int timeSec, boolean me) { }

    public record Leaderboard(ExamInfo exam, int participants, List<BoardRow> rows) { }

    public record OverallRow(int rank, String name, String roll, BigDecimal marks, int timeSec, int taken, boolean me) { }

    public record OverallBoard(String className, int examCount, BigDecimal fullMarks, List<OverallRow> rows) { }

    public record BonusRequest(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal bonus) { }

    public record BonusResult(BigDecimal bonus, int updated, BigDecimal fullMarks) { }

    public record PublishResult(boolean published) { }

    public record AdminSummary(long students, int examCount, List<ExamSummary> pending, List<ExamSummary> published) { }

    public record SectionStudentRow(long id, String name, String roll, @Nullable String phone, int taken, int examCount, BigDecimal marks) { }

    public record SectionStudents(long classId, String className, long sectionId, String section,
                                  int examCount, List<SectionStudentRow> students) { }

    /** One exam in a student's report. The result fields are null when the student did not take it. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportRow(long id, String title, BigDecimal fullMarks, boolean published, boolean taken,
                            BigDecimal bonus, @Nullable BigDecimal marks, @Nullable Integer correct, @Nullable Integer wrong,
                            @Nullable Integer blank, @Nullable Integer timeSec, @Nullable Integer rank,
                            @Nullable Integer participants) { }

    public record ReportSummary(int examCount, int taken, int missed, BigDecimal marks, BigDecimal full) { }

    public record StudentReport(StudentRef student, List<ReportRow> exams, ReportSummary summary) { }
}

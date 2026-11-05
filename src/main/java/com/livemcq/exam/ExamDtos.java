package com.livemcq.exam;

import org.springframework.lang.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Request and response shapes for the admin exam API. */
public final class ExamDtos {

    private ExamDtos() { }

    public record QuestionInput(
            @NotBlank @Size(max = 2000) String body,
            @NotNull @Size(min = 4, max = 4, message = "exactly 4 options are required")
            List<@NotBlank @Size(max = 500) String> options,
            @NotNull @Min(0) @Max(3) Integer correctIdx) { }

    public record CreateExamRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull Long classId,
            @NotNull @Min(1) @Max(180) Integer durationMinutes,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 2) BigDecimal marksPerQuestion,
            @NotNull @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal negativeMarks,
            @NotNull @Size(min = 1, max = 200, message = "add between 1 and 200 questions") List<@Valid @NotNull QuestionInput> questions) { }

    public record ReplaceQuestionsRequest(
            @NotNull @Size(min = 1, max = 200, message = "add between 1 and 200 questions") List<@Valid @NotNull QuestionInput> questions) { }

    public record QuestionView(long id, String body, List<String> options, int correctIdx) { }

    public record ExamSummary(long id, String title, long classId, String className, int durationMinutes,
                              BigDecimal marksPerQuestion, BigDecimal negativeMarks, BigDecimal bonusMarks,
                              int questionCount, BigDecimal baseMarks, BigDecimal fullMarks,
                              boolean published, @Nullable java.time.Instant publishedAt, long attempts) { }

    public record ExamDetail(ExamSummary exam, List<QuestionView> questions) { }
}

package com.livemcq.exam;

import org.springframework.lang.Nullable;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shapes for the student side. Note: the paper never contains the correct answers. */
public final class StudentExamDtos {

    private StudentExamDtos() { }

    public enum Status { AVAILABLE, IN_PROGRESS, SUBMITTED, EXPIRED, MISSED }

    public record StudentExamView(long id, String title, int durationMinutes, BigDecimal marksPerQuestion,
                                  BigDecimal negativeMarks, int questionCount, BigDecimal fullMarks,
                                  boolean published, Status status, @Nullable Long remainingSeconds) { }

    public record PaperQuestion(String body, List<String> options) { }

    public record PaperView(long examId, String title, int durationMinutes, BigDecimal marksPerQuestion,
                            BigDecimal negativeMarks, BigDecimal fullMarks,
                            Instant startedAt, Instant deadlineAt, Instant serverTime, long remainingSeconds,
                            List<PaperQuestion> questions) { }

    /** One entry per question, in paper order; null (or the 4 allowed numbers 0-3) for each. */
    public record SubmitRequest(@NotNull List<Integer> answers) { }

    public record SubmitResult(boolean submitted, int timeSec) { }
}

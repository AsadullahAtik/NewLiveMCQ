package com.livemcq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite primary key (attempt_id, question_id). equals/hashCode are mandatory for JPA keys. */
@Embeddable
public class AttemptAnswerId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "attempt_id")
    private Long attemptId;

    @Column(name = "question_id")
    private Long questionId;

    protected AttemptAnswerId() { }

    public AttemptAnswerId(Long attemptId, Long questionId) {
        this.attemptId = attemptId;
        this.questionId = questionId;
    }

    public Long getAttemptId() { return attemptId; }
    public Long getQuestionId() { return questionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttemptAnswerId other)) return false;   // pattern matching for instanceof (Java 16+)
        return Objects.equals(attemptId, other.attemptId) && Objects.equals(questionId, other.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptId, questionId);
    }
}

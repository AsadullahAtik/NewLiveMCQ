package com.livemcq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The option a student picked for one question. chosenIdx == null means "left blank". */
@Entity
@Table(name = "attempt_answers")
public class AttemptAnswer {

    @EmbeddedId
    private AttemptAnswerId id;

    // repeated here so the database can check that attempt and question belong to the same exam
    @Column(name = "exam_id", nullable = false, updatable = false)
    private Long examId;

    @Column(name = "chosen_idx")
    private Integer chosenIdx;

    protected AttemptAnswer() { }

    public AttemptAnswer(Long attemptId, Long questionId, Long examId, Integer chosenIdx) {
        this.id = new AttemptAnswerId(attemptId, questionId);
        this.examId = examId;
        this.chosenIdx = chosenIdx;
    }

    public AttemptAnswerId getId() { return id; }
    public Long getExamId() { return examId; }
    public Integer getChosenIdx() { return chosenIdx; }
}

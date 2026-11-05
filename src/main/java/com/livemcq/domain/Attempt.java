package com.livemcq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One student's submission for one exam. It stores NO marks: the score is derived from the answers
 * by the attempt_scores view, so it can never disagree with the answers or with the exam's bonus.
 */
@Entity
@Table(name = "attempts")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private Long institutionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false, updatable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "time_sec", nullable = false)
    private int timeSec;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    protected Attempt() { }

    public Attempt(Exam exam, AppUser user, int timeSec) {
        this.exam = exam;
        this.user = user;
        this.institutionId = exam.getInstitutionId();
        this.timeSec = timeSec;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public Exam getExam() { return exam; }
    public AppUser getUser() { return user; }
    public int getTimeSec() { return timeSec; }
    public Instant getSubmittedAt() { return submittedAt; }
}

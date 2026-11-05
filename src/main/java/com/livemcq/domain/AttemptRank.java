package com.livemcq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**
 * Read-only row of the attempt_ranks database view: correct/wrong/blank, marks, full marks and rank.
 * All the scoring and tie-breaking lives in that view (see V1__init_schema.sql); Java only reads it.
 * There are no setters and the class is @Immutable, so Hibernate will never try to write to the view.
 */
@Entity
@Immutable
@Table(name = "attempt_ranks")
public class AttemptRank {

    @Id
    @Column(name = "attempt_id")
    private Long attemptId;

    @Column(name = "exam_id")
    private Long examId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "time_sec")
    private int timeSec;

    private int correct;
    private int wrong;
    private int blank;

    /** marks before the bonus: max(0, correct x perQ - wrong x negative). */
    @Column(name = "raw_marks")
    private BigDecimal rawMarks;

    /** rawMarks + the exam bonus. */
    private BigDecimal marks;

    @Column(name = "bonus_marks")
    private BigDecimal bonusMarks;

    /** questions x perQ + bonus; marks can never exceed this. */
    @Column(name = "full_marks")
    private BigDecimal fullMarks;

    /** 1 = best. Equal marks and equal time share a rank, and the next rank is skipped. */
    @Column(name = "rank_no")
    private int rankNo;

    private int participants;

    protected AttemptRank() { }

    public Long getAttemptId() { return attemptId; }
    public Long getExamId() { return examId; }
    public Long getUserId() { return userId; }
    public Long getInstitutionId() { return institutionId; }
    public int getTimeSec() { return timeSec; }
    public int getCorrect() { return correct; }
    public int getWrong() { return wrong; }
    public int getBlank() { return blank; }
    public BigDecimal getRawMarks() { return rawMarks; }
    public BigDecimal getMarks() { return marks; }
    public BigDecimal getBonusMarks() { return bonusMarks; }
    public BigDecimal getFullMarks() { return fullMarks; }
    public int getRankNo() { return rankNo; }
    public int getParticipants() { return participants; }
}

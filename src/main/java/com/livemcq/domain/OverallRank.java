package com.livemcq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**
 * Read-only row of the overall_ranks view: one student's total over all PUBLISHED exams of the
 * class they are in now, and their rank in that class.
 */
@Entity
@Immutable
@Table(name = "overall_ranks")
public class OverallRank {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "exams_taken")
    private int examsTaken;

    @Column(name = "total_marks")
    private BigDecimal totalMarks;

    @Column(name = "total_time_sec")
    private int totalTimeSec;

    /** how many published exams the class has (not how many this student took). */
    @Column(name = "exam_count")
    private int examCount;

    /** sum of the full marks of those published exams. */
    @Column(name = "full_marks")
    private BigDecimal fullMarks;

    @Column(name = "rank_no")
    private int rankNo;

    private int participants;

    protected OverallRank() { }

    public Long getUserId() { return userId; }
    public Long getInstitutionId() { return institutionId; }
    public Long getClassId() { return classId; }
    public int getExamsTaken() { return examsTaken; }
    public BigDecimal getTotalMarks() { return totalMarks; }
    public int getTotalTimeSec() { return totalTimeSec; }
    public int getExamCount() { return examCount; }
    public BigDecimal getFullMarks() { return fullMarks; }
    public int getRankNo() { return rankNo; }
    public int getParticipants() { return participants; }
}

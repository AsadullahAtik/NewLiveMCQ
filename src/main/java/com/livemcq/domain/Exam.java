package com.livemcq.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exams")
public class Exam {

    private static final BigDecimal MAX_BONUS = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private Long institutionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false, updatable = false)
    private SchoolClass schoolClass;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    // money-like values use BigDecimal, never double: 0.1 + 0.2 must be exactly 0.3
    @Column(name = "marks_per_question", nullable = false, precision = 6, scale = 2)
    private BigDecimal marksPerQuestion;

    @Column(name = "negative_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal negativeMarks;

    @Column(name = "bonus_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal bonusMarks = BigDecimal.ZERO;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // cascade ALL: saving the exam saves its questions; orphanRemoval: dropping one from the list deletes it
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Question> questions = new ArrayList<>();

    protected Exam() { }

    public Exam(SchoolClass schoolClass, String title, int durationMinutes,
                BigDecimal marksPerQuestion, BigDecimal negativeMarks) {
        this.schoolClass = schoolClass;
        this.institutionId = schoolClass.getInstitutionId();
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.marksPerQuestion = marksPerQuestion;
        this.negativeMarks = negativeMarks;
    }

    /** Adds a question at the end and keeps both sides of the relationship in sync. */
    public Question addQuestion(String body, int correctIdx, List<String> options) {
        Question q = new Question(this, questions.size(), body, correctIdx, options);
        questions.add(q);
        return q;
    }

    public boolean isPublished() { return publishedAt != null; }

    public void publish() { publish(Instant.now()); }

    /** The clock is passed in so services (and tests) decide what "now" is. */
    public void publish(Instant at) { this.publishedAt = at; }

    public void unpublish() { this.publishedAt = null; }

    /**
     * The teacher gives every participant the same extra marks (e.g. a question was wrong).
     * Full marks grow by the same amount (see fullMarks), so nobody can score above full marks.
     */
    public void setBonusMarks(BigDecimal bonus) {
        if (bonus == null || bonus.signum() < 0 || bonus.compareTo(MAX_BONUS) > 0) {
            throw new IllegalArgumentException("bonus must be between 0 and 100");
        }
        this.bonusMarks = bonus;
    }

    /** questions x marks per question, before any bonus. */
    public BigDecimal baseMarks() {
        return marksPerQuestion.multiply(BigDecimal.valueOf(questions.size()));
    }

    /** base marks + bonus. This is the number every result is shown "out of". */
    public BigDecimal fullMarks() {
        return baseMarks().add(bonusMarks);
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public SchoolClass getSchoolClass() { return schoolClass; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public BigDecimal getMarksPerQuestion() { return marksPerQuestion; }
    public BigDecimal getNegativeMarks() { return negativeMarks; }
    public BigDecimal getBonusMarks() { return bonusMarks; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Question> getQuestions() { return questions; }
}

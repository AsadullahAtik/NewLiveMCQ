package com.livemcq.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false, updatable = false)
    private Exam exam;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** 0-based index of the right option (0 = first option). */
    @Column(name = "correct_idx", nullable = false)
    private int correctIdx;

    // The options are plain strings kept in their own table (question_options). The list position
    // becomes the "idx" column, so option order is preserved without an extra entity class.
    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "idx")
    @Column(name = "label", nullable = false, columnDefinition = "text")
    private List<String> options = new ArrayList<>();

    protected Question() { }

    Question(Exam exam, int sortOrder, String body, int correctIdx, List<String> options) {
        if (options == null || options.size() != 4) {
            throw new IllegalArgumentException("a question needs exactly 4 options");
        }
        if (correctIdx < 0 || correctIdx >= options.size()) {
            throw new IllegalArgumentException("correct option must be one of the 4 options");
        }
        this.exam = exam;
        this.sortOrder = sortOrder;
        this.body = body;
        this.correctIdx = correctIdx;
        this.options = new ArrayList<>(options);   // defensive copy: the caller cannot change it later
    }

    public Long getId() { return id; }
    public Exam getExam() { return exam; }
    public int getSortOrder() { return sortOrder; }
    public String getBody() { return body; }
    public int getCorrectIdx() { return correctIdx; }
    public List<String> getOptions() { return options; }
}

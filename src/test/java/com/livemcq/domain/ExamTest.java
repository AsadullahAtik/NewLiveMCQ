package com.livemcq.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring, no database. They run in milliseconds. */
class ExamTest {

    private static final List<String> OPTS = List.of("a", "b", "c", "d");

    private static Exam examWith(int questions) {
        Exam e = new Exam(new SchoolClass(1L, "Class 9"), "General Science", 5, new BigDecimal("1"), new BigDecimal("0.25"));
        for (int i = 0; i < questions; i++) {
            e.addQuestion("Q" + i, 0, OPTS);
        }
        return e;
    }

    private static void assertMarks(String expected, BigDecimal actual) {
        // compareTo ignores scale (1.5 == 1.50); equals would not
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0, "expected " + expected + " but was " + actual);
    }

    @Test
    void fullMarksIsQuestionsTimesMarksPerQuestion() {
        assertMarks("5", examWith(5).fullMarks());
        assertMarks("0", examWith(0).fullMarks());
    }

    @Test
    void bonusRaisesFullMarksByTheSameAmount() {
        Exam e = examWith(5);
        e.setBonusMarks(new BigDecimal("1.5"));
        assertMarks("5", e.baseMarks());       // the base does not change
        assertMarks("6.5", e.fullMarks());     // full marks grew with the bonus
        e.setBonusMarks(new BigDecimal("2"));  // setting again replaces, it does not add up to 3.5
        assertMarks("7", e.fullMarks());
        e.setBonusMarks(BigDecimal.ZERO);      // zero removes the bonus
        assertMarks("5", e.fullMarks());
    }

    @Test
    void bonusMustBeBetweenZeroAndOneHundred() {
        Exam e = examWith(2);
        assertThrows(IllegalArgumentException.class, () -> e.setBonusMarks(new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> e.setBonusMarks(new BigDecimal("100.01")));
        assertThrows(IllegalArgumentException.class, () -> e.setBonusMarks(null));
        e.setBonusMarks(new BigDecimal("100"));   // the limits themselves are allowed
        e.setBonusMarks(BigDecimal.ZERO);
        assertMarks("2", e.fullMarks());
    }

    @Test
    void decimalMarksStayExact() {
        Exam e = new Exam(new SchoolClass(1L, "Class 9"), "Decimals", 5, new BigDecimal("0.1"), BigDecimal.ZERO);
        e.addQuestion("a", 0, OPTS);
        e.addQuestion("b", 0, OPTS);
        e.addQuestion("c", 0, OPTS);
        e.setBonusMarks(new BigDecimal("0.2"));
        assertMarks("0.5", e.fullMarks());        // with double, 0.1*3 + 0.2 = 0.5000000000000001
    }

    @Test
    void questionNeedsExactlyFourOptionsAndAValidCorrectIndex() {
        Exam e = examWith(0);
        assertThrows(IllegalArgumentException.class, () -> e.addQuestion("q", 0, List.of("a", "b", "c")));
        assertThrows(IllegalArgumentException.class, () -> e.addQuestion("q", 0, List.of("a", "b", "c", "d", "e")));
        assertThrows(IllegalArgumentException.class, () -> e.addQuestion("q", 0, null));
        assertThrows(IllegalArgumentException.class, () -> e.addQuestion("q", 4, OPTS));
        assertThrows(IllegalArgumentException.class, () -> e.addQuestion("q", -1, OPTS));
        assertEquals(0, e.getQuestions().size());   // nothing half-added after the failures
        e.addQuestion("ok", 3, OPTS);               // the last option is a valid answer
        assertEquals(1, e.getQuestions().size());
    }

    @Test
    void questionsKeepTheirOrderAndCopyTheOptions() {
        Exam e = examWith(0);
        List<String> mine = new ArrayList<>(OPTS);
        Question first = e.addQuestion("first", 1, mine);
        Question second = e.addQuestion("second", 2, OPTS);
        mine.set(1, "CHANGED LATER");
        assertEquals(0, first.getSortOrder());
        assertEquals(1, second.getSortOrder());
        assertEquals("b", first.getOptions().get(1));       // the caller's later edit did not leak in
        assertEquals(1, first.getCorrectIdx());
    }

    @Test
    void publishAndUnpublish() {
        Exam e = examWith(1);
        assertFalse(e.isPublished());
        e.publish();
        assertTrue(e.isPublished());
        e.unpublish();
        assertFalse(e.isPublished());
    }
}

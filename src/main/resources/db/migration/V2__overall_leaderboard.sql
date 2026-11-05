-- Live MCQ  |  V2: class-wide ("overall") leaderboard
--
-- Same rules as the prototype's GET /api/leaderboard/overall:
--  * only PUBLISHED exams of the student's CURRENT class count;
--  * an exam a student did not take adds nothing;
--  * students are ranked by total marks (more first), then by total time (less first);
--    equal marks AND equal time share a rank and the next rank is skipped;
--  * a student who has been moved to another class takes no old results along (they were earned
--    in exams of the old class, which no longer matches the current class).

CREATE VIEW overall_scores AS
SELECT u.id                                   AS user_id,
       u.institution_id,
       sec.class_id,
       count(*)::integer                      AS exams_taken,
       sum(r.marks)::numeric(10, 2)           AS total_marks,
       sum(r.time_sec)::integer               AS total_time_sec
FROM users u
JOIN sections sec      ON sec.id = u.section_id
JOIN attempts a        ON a.user_id = u.id
JOIN exams e           ON e.id = a.exam_id
                      AND e.class_id = sec.class_id
                      AND e.published_at IS NOT NULL
JOIN attempt_scores r  ON r.attempt_id = a.id
WHERE u.role = 'STUDENT'
GROUP BY u.id, u.institution_id, sec.class_id;

-- what the whole class could have scored: the full marks (bonus included) of its published exams
CREATE VIEW class_published_totals AS
SELECT e.class_id,
       count(*)::integer                      AS exam_count,
       sum(t.full_marks)::numeric(10, 2)      AS full_marks
FROM exams e
JOIN exam_totals t ON t.exam_id = e.id
WHERE e.published_at IS NOT NULL
GROUP BY e.class_id;

CREATE VIEW overall_ranks AS
SELECT s.user_id, s.institution_id, s.class_id, s.exams_taken, s.total_marks, s.total_time_sec,
       p.exam_count, p.full_marks,
       rank() OVER (PARTITION BY s.class_id ORDER BY s.total_marks DESC, s.total_time_sec ASC)::integer AS rank_no,
       (count(*) OVER (PARTITION BY s.class_id))::integer                                              AS participants
FROM overall_scores s
JOIN class_published_totals p ON p.class_id = s.class_id;

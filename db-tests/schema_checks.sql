-- Schema checks for V1. Run against an EMPTY database after V1__init_schema.sql has been applied.
-- Any failed assertion raises an exception, so `psql -v ON_ERROR_STOP=1` exits non-zero.
\set ON_ERROR_STOP 1
SET client_min_messages = NOTICE;

CREATE TEMP TABLE t_result (ok boolean, label text);

CREATE FUNCTION check_eq(label text, actual text, expected text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF actual IS NOT DISTINCT FROM expected THEN
        INSERT INTO t_result VALUES (true, label);
        RAISE NOTICE 'PASS  %', label;
    ELSE
        INSERT INTO t_result VALUES (false, label);
        RAISE EXCEPTION 'FAIL  % : expected [%] but got [%]', label, expected, actual;
    END IF;
END $$;

-- the statement must be REJECTED by the database
CREATE FUNCTION check_rejected(label text, stmt text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE stmt;
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO t_result VALUES (true, label);
        RAISE NOTICE 'PASS  % (rejected: %)', label, SQLERRM;
        RETURN;
    END;
    INSERT INTO t_result VALUES (false, label);
    RAISE EXCEPTION 'FAIL  % : statement was accepted but should be rejected', label;
END $$;

-- creates an attempt with answers. answers[i] = chosen option index for question i (0-based), NULL = unanswered.
-- Odd questions with NULL get an explicit NULL row, even ones get no row at all: both must count as blank.
CREATE FUNCTION make_attempt(p_exam bigint, p_user bigint, p_answers int[], p_time int) RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE
    v_attempt bigint; v_inst bigint; q record; v_chosen int;
BEGIN
    SELECT institution_id INTO v_inst FROM exams WHERE id = p_exam;
    INSERT INTO attempts (institution_id, exam_id, user_id, time_sec) VALUES (v_inst, p_exam, p_user, p_time) RETURNING id INTO v_attempt;
    FOR q IN SELECT id, sort_order FROM questions WHERE exam_id = p_exam ORDER BY sort_order LOOP
        v_chosen := p_answers[q.sort_order + 1];
        IF v_chosen IS NOT NULL OR q.sort_order % 2 = 1 THEN
            INSERT INTO attempt_answers (attempt_id, question_id, exam_id, chosen_idx) VALUES (v_attempt, q.id, p_exam, v_chosen);
        END IF;
    END LOOP;
    RETURN v_attempt;
END $$;

-- ------------------------------------------------------------------ fixtures
INSERT INTO institutions (name, slug) VALUES ('Alpha Coaching', 'alpha'), ('Beta School', 'beta');

INSERT INTO school_classes (institution_id, name)
SELECT i.id, c.name FROM institutions i JOIN (VALUES ('alpha','Class 9'),('alpha','Class 10'),('beta','Class 9')) c(slug,name) ON c.slug = i.slug;

INSERT INTO sections (institution_id, class_id, name)
SELECT k.institution_id, k.id, s FROM school_classes k, unnest(ARRAY['A','B']) s;

CREATE TEMP VIEW v AS
SELECT (SELECT id FROM institutions WHERE slug='alpha') AS alpha,
       (SELECT id FROM institutions WHERE slug='beta')  AS beta,
       (SELECT k.id FROM school_classes k JOIN institutions i ON i.id=k.institution_id WHERE i.slug='alpha' AND k.name='Class 9')  AS a9,
       (SELECT k.id FROM school_classes k JOIN institutions i ON i.id=k.institution_id WHERE i.slug='alpha' AND k.name='Class 10') AS a10,
       (SELECT k.id FROM school_classes k JOIN institutions i ON i.id=k.institution_id WHERE i.slug='beta'  AND k.name='Class 9')  AS b9,
       (SELECT s.id FROM sections s JOIN school_classes k ON k.id=s.class_id JOIN institutions i ON i.id=k.institution_id WHERE i.slug='alpha' AND k.name='Class 9' AND s.name='A') AS a9A,
       (SELECT s.id FROM sections s JOIN school_classes k ON k.id=s.class_id JOIN institutions i ON i.id=k.institution_id WHERE i.slug='alpha' AND k.name='Class 9' AND s.name='B') AS a9B,
       (SELECT s.id FROM sections s JOIN school_classes k ON k.id=s.class_id JOIN institutions i ON i.id=k.institution_id WHERE i.slug='beta'  AND k.name='Class 9' AND s.name='A') AS b9A;

INSERT INTO users (institution_id, role, name, roll, password_hash, phone, section_id)
SELECT v.alpha, 'ADMIN', 'Admin User', 'admin', 'x', NULL, NULL FROM v;
INSERT INTO users (institution_id, role, name, roll, password_hash, phone, section_id)
SELECT v.alpha, 'STUDENT', u.name, u.roll, 'x', u.phone, CASE WHEN u.sec = 'A' THEN v.a9A ELSE v.a9B END
FROM v, (VALUES ('Atik','250112','01712345678','A'),('Rafi','101','01811000101','A'),('Tanzila','102','01911000102','A'),
                ('Mehedi','103','01611000103','B'),('Faria','104','01711000104','B'),('Arif','105','01511000105','A')) u(name,roll,phone,sec);
INSERT INTO users (institution_id, role, name, roll, password_hash, section_id)
SELECT v.beta, 'STUDENT', 'Beta Student', '101', 'x', v.b9A FROM v;

-- exam e1: 5 questions, 1 mark each, -0.25 per wrong. Correct options: 0,0,2,1,2
INSERT INTO exams (institution_id, class_id, title, duration_minutes, marks_per_question, negative_marks)
SELECT alpha, a9, 'General Science', 5, 1, 0.25 FROM v;
INSERT INTO questions (exam_id, sort_order, body, correct_idx)
SELECT (SELECT id FROM exams WHERE title='General Science'), g - 1, 'Q' || g, (ARRAY[0,0,2,1,2])[g] FROM generate_series(1,5) g;
INSERT INTO question_options (question_id, idx, label)
SELECT q.id, o, 'opt ' || o FROM questions q, generate_series(0,3) o;

-- ------------------------------------------------------------------ 1. structure
SELECT check_eq('questions have 4 options each', (SELECT count(*) FROM question_options)::text, '20');
SELECT check_eq('exam_totals: 5 questions, base 5.00, full 5.00',
   (SELECT question_count || '/' || base_marks || '/' || full_marks FROM exam_totals), '5/5.00/5.00');

-- ------------------------------------------------------------------ 2. scoring (modelled on the HTML demo)
-- student   : answers (0-based, NULL = blank)                              -> c/w/b  marks  time
-- Atik      : 0,0,2,NULL,0 (q5 wrong, q4 blank)                            -> 3/1/1  2.75   205  rank 6
-- Rafi      : all correct                                                  -> 5/0/0  5.00   212
-- Tanzila   : 4 correct, q4 blank                                          -> 4/0/1  4.00   188
-- Mehedi    : 4 correct, q5 wrong                                          -> 4/1/0  3.75   260
-- Faria     : 4 correct, q5 wrong                                          -> 4/1/0  3.75   260  (ties Mehedi exactly)
-- Arif      : 3 correct, q4+q5 blank                                       -> 3/0/2  3.00   150  rank 5
SELECT make_attempt((SELECT id FROM exams WHERE title='General Science'), (SELECT id FROM users WHERE roll='250112' AND institution_id=(SELECT alpha FROM v)), ARRAY[0,0,2,NULL,0], 205);
SELECT make_attempt((SELECT id FROM exams WHERE title='General Science'), (SELECT id FROM users WHERE roll='101'    AND institution_id=(SELECT alpha FROM v)), ARRAY[0,0,2,1,2], 212);
SELECT make_attempt((SELECT id FROM exams WHERE title='General Science'), (SELECT id FROM users WHERE roll='102'    AND institution_id=(SELECT alpha FROM v)), ARRAY[0,0,2,NULL,2], 188);
SELECT make_attempt((SELECT id FROM exams WHERE title='General Science'), (SELECT id FROM users WHERE roll='103'    AND institution_id=(SELECT alpha FROM v)), ARRAY[0,0,2,1,0], 260);
SELECT make_attempt((SELECT id FROM exams WHERE title='General Science'), (SELECT id FROM users WHERE roll='104'    AND institution_id=(SELECT alpha FROM v)), ARRAY[0,0,2,1,3], 260);
SELECT make_attempt((SELECT id FROM exams WHERE title='General Science'), (SELECT id FROM users WHERE roll='105'    AND institution_id=(SELECT alpha FROM v)), ARRAY[0,0,2,NULL,NULL], 150);

CREATE FUNCTION row_of(p_roll text) RETURNS text LANGUAGE sql AS $$
    SELECT r.correct || '/' || r.wrong || '/' || r.blank || ' marks=' || r.marks || ' rank=' || r.rank_no
    FROM attempt_ranks r JOIN users u ON u.id = r.user_id WHERE u.roll = p_roll AND u.institution_id = (SELECT alpha FROM v) $$;

SELECT check_eq('Atik    3 correct 1 wrong 1 blank = 2.75, rank 6', row_of('250112'), '3/1/1 marks=2.75 rank=6');
SELECT check_eq('Rafi    all correct = 5.00, rank 1',               row_of('101'),    '5/0/0 marks=5.00 rank=1');
SELECT check_eq('Tanzila 4/0/1 = 4.00, rank 2',                     row_of('102'),    '4/0/1 marks=4.00 rank=2');
SELECT check_eq('Mehedi  4/1/0 = 3.75, tied rank 3',                row_of('103'),    '4/1/0 marks=3.75 rank=3');
SELECT check_eq('Faria   4/1/0 = 3.75, tied rank 3',                row_of('104'),    '4/1/0 marks=3.75 rank=3');
SELECT check_eq('Arif    3/0/2 = 3.00, rank 5 (rank 4 skipped after the tie)', row_of('105'), '3/0/2 marks=3.00 rank=5');

-- time is the tie-breaker when marks are equal
SELECT check_eq('ranks come out as 1,2,3,3,5,6 (tie shares a rank, next rank skipped)',
   (SELECT string_agg(rank_no::text, ',' ORDER BY rank_no, time_sec, user_id) FROM attempt_ranks), '1,2,3,3,5,6');
SELECT check_eq('participants counted per exam', (SELECT DISTINCT participants::text FROM attempt_ranks), '6');

-- ------------------------------------------------------------------ 3. bonus: marks and full marks grow together
UPDATE exams SET bonus_marks = 1.5 WHERE title = 'General Science';
SELECT check_eq('bonus 1.5: full marks 6.50', (SELECT full_marks::text FROM exam_totals), '6.50');
SELECT check_eq('bonus 1.5: Rafi 6.50 (= full marks)', row_of('101'), '5/0/0 marks=6.50 rank=1');
SELECT check_eq('bonus 1.5: Atik 4.25',                row_of('250112'), '3/1/1 marks=4.25 rank=6');
SELECT check_eq('bonus never lets a mark exceed full marks',
   (SELECT count(*)::text FROM attempt_ranks WHERE marks > full_marks), '0');
SELECT check_eq('bonus does not change the ranking order',
   (SELECT string_agg(r.rank_no::text, ',' ORDER BY r.rank_no, r.time_sec, r.user_id) FROM attempt_ranks r), '1,2,3,3,5,6');
UPDATE exams SET bonus_marks = 2 WHERE title = 'General Science';
SELECT check_eq('re-applying a bonus replaces it (not 3.5): Rafi 7.00 of 7.00',
   (SELECT marks || '/' || full_marks FROM attempt_ranks r JOIN users u ON u.id = r.user_id WHERE u.roll='101' AND u.institution_id=(SELECT alpha FROM v)), '7.00/7.00');
UPDATE exams SET bonus_marks = 0 WHERE title = 'General Science';
SELECT check_eq('bonus 0 restores the original marks', row_of('250112'), '3/1/1 marks=2.75 rank=6');

-- ------------------------------------------------------------------ 4. negative marking floors at zero, bonus still added
INSERT INTO exams (institution_id, class_id, title, duration_minutes, marks_per_question, negative_marks, bonus_marks)
SELECT alpha, a9, 'Floor test', 3, 1, 1, 2 FROM v;
INSERT INTO questions (exam_id, sort_order, body, correct_idx)
SELECT (SELECT id FROM exams WHERE title='Floor test'), g - 1, 'F' || g, 0 FROM generate_series(1,3) g;
SELECT make_attempt((SELECT id FROM exams WHERE title='Floor test'), (SELECT id FROM users WHERE roll='101' AND institution_id=(SELECT alpha FROM v)), ARRAY[1,1,1], 30);
SELECT check_eq('3 wrong at -1 each: raw floors at 0, then +2 bonus = 2.00 of 5.00',
   (SELECT raw_marks || '/' || marks || '/' || full_marks FROM attempt_ranks r JOIN exams e ON e.id = r.exam_id WHERE e.title='Floor test'), '0.00/2.00/5.00');

-- an attempt that has no answer rows at all is all blank, not missing from the results
INSERT INTO attempts (institution_id, exam_id, user_id, time_sec)
SELECT alpha, (SELECT id FROM exams WHERE title='Floor test'), (SELECT id FROM users WHERE roll='250112' AND institution_id=alpha), 10 FROM v;
SELECT check_eq('attempt with no answer rows is listed with 3 blank',
   (SELECT blank || '/' || marks FROM attempt_ranks r JOIN users u ON u.id = r.user_id JOIN exams e ON e.id = r.exam_id
    WHERE e.title='Floor test' AND u.roll='250112' AND u.institution_id=(SELECT alpha FROM v)), '3/2.00');

-- ------------------------------------------------------------------ 5. integrity: bad data must be rejected
SELECT check_rejected('one attempt per student per exam',
   $q$ INSERT INTO attempts (institution_id, exam_id, user_id, time_sec)
       SELECT alpha, (SELECT id FROM exams WHERE title='Floor test'), (SELECT id FROM users WHERE roll='101' AND institution_id=alpha), 5 FROM v $q$);
SELECT check_rejected('roll is unique per institution ignoring case',
   $q$ INSERT INTO users (institution_id, role, name, roll, password_hash, section_id)
       SELECT alpha, 'STUDENT', 'Duplicate', 'ADMIN', 'x', a9A FROM v $q$);
SELECT check_eq('...but the same roll is fine in another institution',
   (SELECT count(*)::text FROM users WHERE roll = '101'), '2');
SELECT check_rejected('student without a section',
   $q$ INSERT INTO users (institution_id, role, name, roll, password_hash) SELECT alpha, 'STUDENT', 'No Section', '999', 'x' FROM v $q$);
SELECT check_rejected('student placed in another institution''s section',
   $q$ INSERT INTO users (institution_id, role, name, roll, password_hash, section_id) SELECT alpha, 'STUDENT', 'Cross Tenant', '998', 'x', b9A FROM v $q$);
SELECT check_rejected('exam for another institution''s class',
   $q$ INSERT INTO exams (institution_id, class_id, title, duration_minutes, marks_per_question) SELECT alpha, b9, 'Cross Tenant Exam', 5, 1 FROM v $q$);
SELECT check_rejected('attempt by a student of another institution',
   $q$ INSERT INTO attempts (institution_id, exam_id, user_id, time_sec)
       SELECT alpha, (SELECT id FROM exams WHERE title='General Science'), (SELECT id FROM users WHERE name='Beta Student'), 5 FROM v $q$);
SELECT check_rejected('answer pointing at a question of a different exam',
   $q$ INSERT INTO attempt_answers (attempt_id, question_id, exam_id, chosen_idx)
       SELECT (SELECT a.id FROM attempts a WHERE a.exam_id = (SELECT id FROM exams WHERE title='Floor test') LIMIT 1),
              (SELECT id FROM questions WHERE exam_id = (SELECT id FROM exams WHERE title='General Science') LIMIT 1),
              (SELECT id FROM exams WHERE title='Floor test'), 0 $q$);
SELECT check_rejected('same question answered twice in one attempt',
   $q$ INSERT INTO attempt_answers (attempt_id, question_id, exam_id, chosen_idx)
       SELECT aa.attempt_id, aa.question_id, aa.exam_id, 1 FROM attempt_answers aa LIMIT 1 $q$);
SELECT check_rejected('chosen option 4 does not exist',
   $q$ UPDATE attempt_answers SET chosen_idx = 4 WHERE ctid = (SELECT ctid FROM attempt_answers LIMIT 1) $q$);
SELECT check_rejected('bonus above 100',      $q$ UPDATE exams SET bonus_marks = 101 $q$);
SELECT check_rejected('negative bonus',        $q$ UPDATE exams SET bonus_marks = -1 $q$);
SELECT check_rejected('negative marking below 0', $q$ UPDATE exams SET negative_marks = -0.5 $q$);
SELECT check_rejected('zero marks per question',  $q$ UPDATE exams SET marks_per_question = 0 $q$);
SELECT check_rejected('duration 0 minutes',    $q$ UPDATE exams SET duration_minutes = 0 $q$);
SELECT check_rejected('duration 181 minutes',  $q$ UPDATE exams SET duration_minutes = 181 $q$);
SELECT check_rejected('exam title too short',  $q$ UPDATE exams SET title = 'ab' $q$);
SELECT check_rejected('correct option index 4',$q$ UPDATE questions SET correct_idx = 4 $q$);
SELECT check_rejected('blank option text',     $q$ UPDATE question_options SET label = '   ' $q$);
SELECT check_rejected('duplicate question order in one exam',
   $q$ INSERT INTO questions (exam_id, sort_order, body, correct_idx) SELECT exam_id, 0, 'dup', 0 FROM questions LIMIT 1 $q$);
SELECT check_rejected('bad phone number',      $q$ UPDATE users SET phone = '12ab' WHERE roll = '101' $q$);
SELECT check_rejected('unknown role',          $q$ UPDATE users SET role = 'PRINCIPAL' WHERE roll = 'admin' $q$);
SELECT check_rejected('bad institution slug',  $q$ INSERT INTO institutions (name, slug) VALUES ('Bad Slug', 'Bad Slug!') $q$);
SELECT check_rejected('time of 0 seconds',     $q$ UPDATE attempts SET time_sec = 0 $q$);
SELECT check_rejected('deleting a section that still has students',
   $q$ DELETE FROM sections WHERE id = (SELECT a9A FROM v) $q$);
SELECT check_rejected('deleting an institution that still has data', $q$ DELETE FROM institutions WHERE slug = 'alpha' $q$);

-- ------------------------------------------------------------------ 5b. exam sessions (V3)
INSERT INTO exam_sessions (institution_id, exam_id, user_id, started_at, deadline_at)
  SELECT a.institution_id, a.exam_id, a.user_id, now(), now() + interval '30 minutes' FROM attempts a ORDER BY a.id LIMIT 1;
SELECT check_eq('a session row can be stored', (SELECT count(*)::text FROM exam_sessions), '1');
SELECT check_rejected('second session for the same student and exam',
   $q$ INSERT INTO exam_sessions (institution_id, exam_id, user_id, started_at, deadline_at)
       SELECT institution_id, exam_id, user_id, now(), now() + interval '1 minute' FROM exam_sessions $q$);
SELECT check_rejected('deadline not after start',
   $q$ UPDATE exam_sessions SET deadline_at = started_at $q$);
SELECT check_rejected('session pointing at a student of another institution',
   $q$ INSERT INTO exam_sessions (institution_id, exam_id, user_id, started_at, deadline_at)
       SELECT e.institution_id, e.id, u.id, now(), now() + interval '1 minute'
       FROM exams e, users u WHERE u.institution_id <> e.institution_id LIMIT 1 $q$);

-- ------------------------------------------------------------------ 6. delete cascades
SELECT check_eq('before delete: Floor test has questions, an attempt and answers',
   (SELECT (SELECT count(*) FROM questions WHERE exam_id=e.id) || '/' || (SELECT count(*) FROM attempts WHERE exam_id=e.id) || '/' || (SELECT count(*) FROM attempt_answers WHERE exam_id=e.id)
    FROM exams e WHERE e.title='Floor test'), '3/2/3');
DELETE FROM exams WHERE title = 'Floor test';
SELECT check_eq('deleting an exam removes its questions, options, attempts and answers',
   (SELECT (SELECT count(*) FROM questions WHERE body LIKE 'F%') || '/' || (SELECT count(*) FROM attempts a WHERE NOT EXISTS (SELECT 1 FROM exams e WHERE e.id=a.exam_id)) || '/' ||
           (SELECT count(*) FROM attempt_answers aa WHERE NOT EXISTS (SELECT 1 FROM attempts a WHERE a.id=aa.attempt_id))), '0/0/0');
SELECT check_eq('the other exam is untouched (still 6 attempts)',
   (SELECT count(*)::text FROM attempts), '6');

DO $$ DECLARE p int; f int; BEGIN
  SELECT count(*) FILTER (WHERE ok), count(*) FILTER (WHERE NOT ok) INTO p, f FROM t_result;
  RAISE NOTICE '== % checks passed, % failed ==', p, f;
END $$;

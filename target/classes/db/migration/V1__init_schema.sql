-- Live MCQ  |  V1: core schema
--
-- Design notes
--  * Multi-tenant from day one: every table that holds institution data carries institution_id,
--    and composite foreign keys (id, institution_id) make it impossible to link rows across institutions.
--  * Marks are never stored. They are derived from the answers (see attempt_scores), so changing the
--    exam bonus or the negative marking can never leave stale numbers behind.
--  * Full marks = question_count * marks_per_question + bonus_marks, so no mark can exceed it.

CREATE TABLE institutions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(150) NOT NULL CHECK (char_length(btrim(name)) >= 2),
    slug        VARCHAR(60)  NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE school_classes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    institution_id  BIGINT       NOT NULL REFERENCES institutions (id),
    name            VARCHAR(80)  NOT NULL CHECK (char_length(btrim(name)) >= 2),
    UNIQUE (institution_id, name),
    UNIQUE (id, institution_id)
);

CREATE TABLE sections (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    institution_id  BIGINT      NOT NULL,
    class_id        BIGINT      NOT NULL,
    name            VARCHAR(40) NOT NULL CHECK (char_length(btrim(name)) >= 1),
    UNIQUE (class_id, name),
    UNIQUE (id, institution_id),
    FOREIGN KEY (class_id, institution_id) REFERENCES school_classes (id, institution_id) ON DELETE CASCADE
);

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    institution_id  BIGINT       NOT NULL REFERENCES institutions (id),
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT')),
    name            VARCHAR(120) NOT NULL CHECK (char_length(btrim(name)) >= 3),
    roll            VARCHAR(20)  NOT NULL CHECK (char_length(btrim(roll)) >= 1),
    password_hash   VARCHAR(100) NOT NULL,
    phone           VARCHAR(20)  CHECK (phone IS NULL OR phone ~ '^[0-9]{6,15}$'),
    section_id      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (id, institution_id),
    -- a student always belongs to a class section; staff do not have to
    CHECK (role <> 'STUDENT' OR section_id IS NOT NULL),
    FOREIGN KEY (section_id, institution_id) REFERENCES sections (id, institution_id)
);
-- roll numbers are unique per institution, ignoring case ("A1" and "a1" are the same login)
CREATE UNIQUE INDEX uq_users_institution_roll ON users (institution_id, lower(roll));
CREATE INDEX idx_users_section ON users (section_id);

CREATE TABLE exams (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    institution_id      BIGINT        NOT NULL,
    class_id            BIGINT        NOT NULL,
    title               VARCHAR(200)  NOT NULL CHECK (char_length(btrim(title)) >= 3),
    duration_minutes    INTEGER       NOT NULL CHECK (duration_minutes BETWEEN 1 AND 180),
    marks_per_question  NUMERIC(6, 2) NOT NULL CHECK (marks_per_question > 0),
    negative_marks      NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (negative_marks >= 0),
    bonus_marks         NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (bonus_marks BETWEEN 0 AND 100),
    published_at        TIMESTAMPTZ,                      -- NULL = results not published
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (id, institution_id),
    FOREIGN KEY (class_id, institution_id) REFERENCES school_classes (id, institution_id)
);
CREATE INDEX idx_exams_class ON exams (class_id);

CREATE TABLE questions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exam_id      BIGINT  NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    sort_order   INTEGER NOT NULL CHECK (sort_order >= 0),
    body         TEXT    NOT NULL CHECK (char_length(btrim(body)) > 0),
    correct_idx  INTEGER NOT NULL CHECK (correct_idx BETWEEN 0 AND 3),
    UNIQUE (exam_id, sort_order),
    UNIQUE (id, exam_id)
);

CREATE TABLE question_options (
    question_id  BIGINT  NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    idx          INTEGER NOT NULL CHECK (idx BETWEEN 0 AND 3),
    label        TEXT    NOT NULL CHECK (char_length(btrim(label)) > 0),
    PRIMARY KEY (question_id, idx)
);

CREATE TABLE attempts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    institution_id  BIGINT      NOT NULL,
    exam_id         BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    time_sec        INTEGER     NOT NULL CHECK (time_sec >= 1),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (exam_id, user_id),                            -- one attempt per student per exam
    UNIQUE (id, exam_id),
    FOREIGN KEY (exam_id, institution_id) REFERENCES exams (id, institution_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id, institution_id) REFERENCES users (id, institution_id)
);
CREATE INDEX idx_attempts_user ON attempts (user_id);

-- One row per answered question. A question with no row (or chosen_idx NULL) counts as unanswered.
CREATE TABLE attempt_answers (
    attempt_id   BIGINT  NOT NULL,
    question_id  BIGINT  NOT NULL,
    exam_id      BIGINT  NOT NULL,
    chosen_idx   INTEGER CHECK (chosen_idx BETWEEN 0 AND 3),
    PRIMARY KEY (attempt_id, question_id),
    -- the attempt and the question must belong to the same exam
    FOREIGN KEY (attempt_id, exam_id)  REFERENCES attempts (id, exam_id)  ON DELETE CASCADE,
    FOREIGN KEY (question_id, exam_id) REFERENCES questions (id, exam_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------------------------
-- Derived data
-- ---------------------------------------------------------------------------------------------

CREATE VIEW exam_totals AS
SELECT e.id                                                                    AS exam_id,
       count(q.id)::integer                                                    AS question_count,
       (count(q.id) * e.marks_per_question)::numeric(8, 2)                     AS base_marks,
       (count(q.id) * e.marks_per_question + e.bonus_marks)::numeric(8, 2)     AS full_marks
FROM exams e
LEFT JOIN questions q ON q.exam_id = e.id
GROUP BY e.id;

-- correct / wrong / blank per attempt, then marks:
--   raw   = max(0, correct * marks_per_question - wrong * negative_marks)
--   marks = raw + exam bonus
CREATE VIEW attempt_scores AS
SELECT c.attempt_id, c.exam_id, c.user_id, c.institution_id, c.time_sec,
       c.correct, c.wrong, c.blank,
       GREATEST(0, c.correct * c.marks_per_question - c.wrong * c.negative_marks)::numeric(8, 2)
           AS raw_marks,
       (GREATEST(0, c.correct * c.marks_per_question - c.wrong * c.negative_marks) + c.bonus_marks)::numeric(8, 2)
           AS marks,
       c.bonus_marks
FROM (
    SELECT a.id AS attempt_id, a.exam_id, a.user_id, a.institution_id, a.time_sec,
           e.marks_per_question, e.negative_marks, e.bonus_marks,
           (count(*) FILTER (WHERE aa.chosen_idx IS NOT NULL AND aa.chosen_idx = q.correct_idx))::integer  AS correct,
           (count(*) FILTER (WHERE aa.chosen_idx IS NOT NULL AND aa.chosen_idx <> q.correct_idx))::integer AS wrong,
           (count(*) FILTER (WHERE aa.chosen_idx IS NULL))::integer                                        AS blank
    FROM attempts a
    JOIN exams e     ON e.id = a.exam_id
    JOIN questions q ON q.exam_id = e.id
    LEFT JOIN attempt_answers aa ON aa.attempt_id = a.id AND aa.question_id = q.id
    GROUP BY a.id, e.id
) c;

-- Rank inside an exam: more marks first, then less time. Equal marks AND equal time share a rank,
-- and the next rank is skipped (1, 2, 2, 4), exactly like the prototype.
CREATE VIEW attempt_ranks AS
SELECT s.*,
       t.full_marks,
       rank() OVER (PARTITION BY s.exam_id ORDER BY s.marks DESC, s.time_sec ASC)::integer AS rank_no,
       (count(*) OVER (PARTITION BY s.exam_id))::integer                                  AS participants
FROM attempt_scores s
JOIN exam_totals t ON t.exam_id = s.exam_id;

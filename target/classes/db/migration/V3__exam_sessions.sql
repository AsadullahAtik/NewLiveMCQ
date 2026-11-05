-- A student "starts" an exam: the SERVER records when, and works out the deadline itself.
-- The client's clock is never trusted; at submit time the server compares now() with deadline_at.
-- One session per student per exam: pressing "start" again (page reload, app restart) resumes it.
CREATE TABLE exam_sessions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    institution_id  BIGINT      NOT NULL,
    exam_id         BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    deadline_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (exam_id, user_id),
    CHECK (deadline_at > started_at),
    FOREIGN KEY (exam_id, institution_id) REFERENCES exams (id, institution_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id, institution_id) REFERENCES users (id, institution_id)
);
CREATE INDEX idx_exam_sessions_user ON exam_sessions (user_id);

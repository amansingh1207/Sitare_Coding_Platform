CREATE TABLE submission_test_results (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    test_case_id BIGINT NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    actual_output TEXT,
    runtime_ms INTEGER,
    memory_used_kb INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_submission_test_results_submission_id ON submission_test_results(submission_id);
CREATE INDEX idx_submission_test_results_test_case_id ON submission_test_results(test_case_id);

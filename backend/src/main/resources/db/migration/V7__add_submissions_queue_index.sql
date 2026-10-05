-- Queue position lookups (count of PENDING rows submitted earlier)
-- run on every detail poll while a submission waits.
CREATE INDEX IF NOT EXISTS idx_submissions_status_submitted_at
    ON submissions(status, submitted_at);

-- Presence tracking for the admin traffic view: last activity per user.
-- Nullable so pre-existing rows need no backfill; only heartbeat writers
-- ever set it.
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMP;

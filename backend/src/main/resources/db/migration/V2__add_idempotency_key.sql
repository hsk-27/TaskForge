-- TaskForge Database Schema Migration V2
-- Adds idempotency key support for distributed deduplication and exactly-once ingestion guarantees

ALTER TABLE jobs ADD COLUMN idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX idx_jobs_idempotency_key ON jobs(idempotency_key);

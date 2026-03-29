-- Add pause/resume timestamp fields
ALTER TABLE voice_interview_sessions
ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS resumed_at TIMESTAMP;

-- Add indexes for common queries
CREATE INDEX IF NOT EXISTS idx_voice_interview_sessions_status
ON voice_interview_sessions(status);

CREATE INDEX IF NOT EXISTS idx_voice_interview_sessions_updated_at
ON voice_interview_sessions(updated_at);

-- Note: status field remains VARCHAR(20)
-- Enum mapping handled in Java code (IN_PROGRESS, PAUSED, COMPLETED, FAILED)

-- Voice interview sessions table
CREATE TABLE voice_interview_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255),
    role_type VARCHAR(50) NOT NULL,
    custom_jd_text TEXT,

    -- Phase configuration
    intro_enabled BOOLEAN DEFAULT TRUE,
    tech_enabled BOOLEAN DEFAULT TRUE,
    project_enabled BOOLEAN DEFAULT TRUE,
    hr_enabled BOOLEAN DEFAULT TRUE,

    -- Session state
    current_phase VARCHAR(20),
    status VARCHAR(20) DEFAULT 'IN_PROGRESS',

    -- Timing
    planned_duration INT DEFAULT 30,
    actual_duration INT,
    start_time TIMESTAMP DEFAULT NOW(),
    end_time TIMESTAMP,

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Voice interview messages table
CREATE TABLE voice_interview_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT REFERENCES voice_interview_sessions(id) ON DELETE CASCADE,

    message_type VARCHAR(20) NOT NULL,
    phase VARCHAR(20),

    -- User speech
    user_recognized_text TEXT,

    -- AI response
    ai_generated_text TEXT,

    -- Metadata
    timestamp TIMESTAMP DEFAULT NOW(),
    sequence_num INT,

    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_session_messages ON voice_interview_messages(session_id);
CREATE INDEX idx_message_timestamp ON voice_interview_messages(timestamp);

-- Voice interview evaluations table
CREATE TABLE voice_interview_evaluations (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT REFERENCES voice_interview_sessions(id) UNIQUE,

    -- Overall score
    overall_score INT,
    overall_rating VARCHAR(20),

    -- Dimension scores
    tech_knowledge_score INT,
    tech_knowledge_comment TEXT,

    project_exp_score INT,
    project_exp_comment TEXT,

    communication_score INT,
    communication_comment TEXT,

    logical_thinking_score INT,
    logical_thinking_comment TEXT,

    -- Suggestions and summary
    improvement_suggestions TEXT,
    strengths_summary TEXT,

    -- Metadata
    interviewer_role VARCHAR(50),
    interview_date TIMESTAMP,

    created_at TIMESTAMP DEFAULT NOW()
);

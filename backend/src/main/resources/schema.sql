CREATE TABLE IF NOT EXISTS recruitment_job (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_code VARCHAR(64) NOT NULL UNIQUE,
    job_title VARCHAR(128) NOT NULL,
    department_name VARCHAR(128) NOT NULL,
    requirements VARCHAR(2000) NOT NULL,
    responsibilities VARCHAR(2000) NOT NULL,
    publish_date DATE NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recruitment_candidate (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    full_name VARCHAR(64) NOT NULL,
    mobile_phone VARCHAR(32) NOT NULL,
    major VARCHAR(128) NOT NULL,
    application_status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    interview_stage_status VARCHAR(64) NOT NULL DEFAULT '简历待查',
    interviewee_user_id INTEGER,
    interview_process_id INTEGER,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES recruitment_job(id)
);

CREATE INDEX IF NOT EXISTS idx_recruitment_job_status ON recruitment_job(status);
CREATE INDEX IF NOT EXISTS idx_recruitment_candidate_job_id ON recruitment_candidate(job_id);
CREATE INDEX IF NOT EXISTS idx_recruitment_candidate_status ON recruitment_candidate(application_status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_recruitment_candidate_job_interviewee ON recruitment_candidate(job_id, interviewee_user_id);

CREATE TABLE IF NOT EXISTS interview_batch (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    batch_code VARCHAR(64) NOT NULL UNIQUE,
    batch_name VARCHAR(128) NOT NULL,
    job_id INTEGER,
    start_time DATETIME,
    end_time DATETIME,
    description VARCHAR(1000),
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_question (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    question_title VARCHAR(128) NOT NULL,
    question_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    difficulty VARCHAR(32),
    tags VARCHAR(255),
    content VARCHAR(3000) NOT NULL,
    reference_answer VARCHAR(3000),
    score INTEGER NOT NULL DEFAULT 10,
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_candidate (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    batch_id INTEGER NOT NULL,
    recruitment_candidate_id INTEGER NOT NULL,
    candidate_name VARCHAR(64) NOT NULL,
    mobile_phone VARCHAR(32) NOT NULL,
    interview_status VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED',
    total_score INTEGER NOT NULL DEFAULT 0,
    interviewer_comment VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES interview_batch(id)
);

CREATE TABLE IF NOT EXISTS interview_submission (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    interview_candidate_id INTEGER NOT NULL,
    question_id INTEGER NOT NULL,
    answer_content VARCHAR(5000) NOT NULL,
    score INTEGER,
    reviewer_comment VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (interview_candidate_id) REFERENCES interview_candidate(id),
    FOREIGN KEY (question_id) REFERENCES interview_question(id)
);

CREATE INDEX IF NOT EXISTS idx_interview_batch_status ON interview_batch(status);
CREATE INDEX IF NOT EXISTS idx_interview_question_status ON interview_question(status);
CREATE INDEX IF NOT EXISTS idx_interview_candidate_batch_id ON interview_candidate(batch_id);
CREATE INDEX IF NOT EXISTS idx_interview_submission_candidate_id ON interview_submission(interview_candidate_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_candidate_batch_recruitment ON interview_candidate(batch_id, recruitment_candidate_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_submission_candidate_question ON interview_submission(interview_candidate_id, question_id);

CREATE TABLE IF NOT EXISTS sys_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    display_name VARCHAR(64),
    mobile_phone VARCHAR(32),
    mobile_phone_normalized VARCHAR(32),
    email VARCHAR(128),
    email_normalized VARCHAR(128),
    status INTEGER NOT NULL DEFAULT 1,
    profile_completed INTEGER NOT NULL DEFAULT 0,
    token_version INTEGER NOT NULL DEFAULT 0,
    must_change_password INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sys_user_mobile_phone_normalized ON sys_user(mobile_phone_normalized);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sys_user_email_normalized ON sys_user(email_normalized);

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operator_user_id INTEGER,
    operator_username VARCHAR(64),
    operator_role_code VARCHAR(32),
    module_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(128),
    detail VARCHAR(4000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_audit_log_module_code ON sys_audit_log(module_code);
CREATE INDEX IF NOT EXISTS idx_sys_audit_log_operator_user_id ON sys_audit_log(operator_user_id);
CREATE INDEX IF NOT EXISTS idx_sys_audit_log_created_at ON sys_audit_log(created_at);

CREATE TABLE IF NOT EXISTS interview_knowledge_base (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    knowledge_base_name VARCHAR(128) NOT NULL,
    tech_category VARCHAR(128),
    job_category VARCHAR(128),
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_knowledge_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    knowledge_base_id INTEGER NOT NULL,
    knowledge_point VARCHAR(255) NOT NULL,
    knowledge_content VARCHAR(5000) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (knowledge_base_id) REFERENCES interview_knowledge_base(id)
);

CREATE TABLE IF NOT EXISTS interview_job_knowledge_weight (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    knowledge_base_id INTEGER NOT NULL,
    weight INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_llm_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_name VARCHAR(128) NOT NULL,
    model_role VARCHAR(32) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(512),
    model_name VARCHAR(128) NOT NULL,
    prompt_template VARCHAR(5000),
    scoring_rule_prompt VARCHAR(5000),
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_process (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recruitment_candidate_id INTEGER NOT NULL,
    interviewee_user_id INTEGER,
    job_id INTEGER NOT NULL,
    template_id INTEGER,
    template_name VARCHAR(128),
    current_stage VARCHAR(32) NOT NULL,
    stage_status VARCHAR(32) NOT NULL,
    overall_status VARCHAR(32) NOT NULL,
    ai_threshold_score INTEGER NOT NULL DEFAULT 70,
    ai_follow_up_threshold INTEGER NOT NULL DEFAULT 70,
    ai_average_score INTEGER,
    ai_min_question_rounds INTEGER NOT NULL DEFAULT 5,
    ai_max_question_rounds INTEGER NOT NULL DEFAULT 10,
    anti_cheat_switch_limit INTEGER NOT NULL DEFAULT 5,
    anti_cheat_switch_count INTEGER NOT NULL DEFAULT 0,
    ai_output_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ai_recording_path VARCHAR(500),
    ai_recording_file_name VARCHAR(255),
    video_approved INTEGER NOT NULL DEFAULT 0,
    onsite_approved INTEGER NOT NULL DEFAULT 0,
    approved_hr_user_id INTEGER,
    approved_hr_name VARCHAR(64),
    process_status_view VARCHAR(64) NOT NULL,
    remark VARCHAR(2000),
    last_heartbeat_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_ai_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    process_id INTEGER NOT NULL,
    process_stage_id INTEGER,
    stage_scope_id INTEGER NOT NULL DEFAULT 0,
    knowledge_base_id INTEGER,
    knowledge_point VARCHAR(255),
    question_content VARCHAR(5000) NOT NULL,
    question_status VARCHAR(32) NOT NULL DEFAULT 'READY',
    question_generation_attempts INTEGER NOT NULL DEFAULT 0,
    question_generation_token VARCHAR(64),
    question_lease_expires_at DATETIME,
    question_next_retry_at DATETIME,
    question_generation_error VARCHAR(1000),
    previous_record_id INTEGER,
    suggested_next_question VARCHAR(5000),
    answer_content VARCHAR(5000),
    answer_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    answer_processing_token VARCHAR(64),
    answer_lease_expires_at DATETIME,
    answer_processing_attempts INTEGER NOT NULL DEFAULT 0,
    answer_error VARCHAR(1000),
    interviewer_score INTEGER,
    scorer_score INTEGER,
    average_score INTEGER,
    interviewer_comment VARCHAR(2000),
    sequence_no INTEGER NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_video_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    process_id INTEGER NOT NULL,
    process_stage_id INTEGER,
    stage_scope_id INTEGER NOT NULL DEFAULT 0,
    video_serial_no VARCHAR(128) NOT NULL,
    video_join_link VARCHAR(500) NOT NULL,
    approver_user_id INTEGER,
    approver_name VARCHAR(64),
    interviewee_join_time DATETIME,
    hr_join_time DATETIME,
    start_time DATETIME,
    end_time DATETIME,
    recording_end_requested_at DATETIME,
    recording_path VARCHAR(500),
    hr_recording_path VARCHAR(500),
    hr_recording_file_name VARCHAR(255),
    interviewee_recording_path VARCHAR(500),
    interviewee_recording_file_name VARCHAR(255),
    merged_recording_path VARCHAR(500),
    merged_recording_file_name VARCHAR(255),
    audio_path VARCHAR(500),
    audio_file_name VARCHAR(255),
    transcript_text TEXT,
    summary_text TEXT,
    summary_status VARCHAR(128),
    hr_offer_sdp TEXT,
    interviewee_answer_sdp TEXT,
    hr_ice_candidates TEXT,
    interviewee_ice_candidates TEXT,
    recording_file_name VARCHAR(255),
    session_status VARCHAR(32) NOT NULL,
    last_activity_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_interview_video_session_stage_scope
        CHECK (stage_scope_id = COALESCE(process_stage_id, 0))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_job_knowledge_weight_job_base
    ON interview_job_knowledge_weight(job_id, knowledge_base_id);

CREATE TABLE IF NOT EXISTS interview_process_template (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000),
    status INTEGER NOT NULL DEFAULT 1,
    version INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_process_template_stage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_id INTEGER NOT NULL,
    stage_name VARCHAR(128) NOT NULL,
    stage_type VARCHAR(32) NOT NULL,
    knowledge_base_id INTEGER,
    round_knowledge_points VARCHAR(2000),
    sequence_no INTEGER NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_process_stage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    process_id INTEGER NOT NULL,
    template_stage_id INTEGER,
    stage_name VARCHAR(128) NOT NULL,
    stage_type VARCHAR(32) NOT NULL,
    knowledge_base_id INTEGER,
    round_knowledge_points VARCHAR(2000),
    sequence_no INTEGER NOT NULL,
    stage_status VARCHAR(32) NOT NULL,
    approved INTEGER,
    approved_hr_user_id INTEGER,
    approved_hr_name VARCHAR(64),
    ai_recording_path VARCHAR(500),
    ai_recording_file_name VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_process_candidate_id ON interview_process(recruitment_candidate_id);
CREATE INDEX IF NOT EXISTS idx_interview_process_template_id ON interview_process(template_id);
CREATE INDEX IF NOT EXISTS idx_interview_process_stage ON interview_process(current_stage);
CREATE INDEX IF NOT EXISTS idx_interview_ai_record_process_id ON interview_ai_record(process_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_ai_record_scope_sequence ON interview_ai_record(process_id, stage_scope_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_interview_ai_record_question_retry ON interview_ai_record(question_status, question_next_retry_at);
CREATE INDEX IF NOT EXISTS idx_interview_ai_record_answer_lease ON interview_ai_record(answer_status, answer_lease_expires_at);
CREATE INDEX IF NOT EXISTS idx_interview_ai_record_process_stage_id ON interview_ai_record(process_stage_id);
CREATE INDEX IF NOT EXISTS idx_interview_video_session_process_id ON interview_video_session(process_id);
CREATE INDEX IF NOT EXISTS idx_interview_video_session_process_stage_id ON interview_video_session(process_stage_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_interview_video_session_process_scope ON interview_video_session(process_id, stage_scope_id);
CREATE INDEX IF NOT EXISTS idx_interview_process_template_stage_template_id ON interview_process_template_stage(template_id);
CREATE INDEX IF NOT EXISTS idx_interview_process_stage_process_id ON interview_process_stage(process_id);

-- School examination extension. The legacy recruitment/interview tables remain available for
-- historical data, while these tables own the school-facing class, student, exam and analysis model.
CREATE TABLE IF NOT EXISTS school_class (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    major_name VARCHAR(128) NOT NULL,
    class_name VARCHAR(128) NOT NULL,
    class_code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(1000),
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS school_student (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_no VARCHAR(64) NOT NULL UNIQUE,
    full_name VARCHAR(64) NOT NULL,
    class_id INTEGER NOT NULL,
    user_id INTEGER UNIQUE,
    status INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (class_id) REFERENCES school_class(id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS school_exam (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    exam_code VARCHAR(64) NOT NULL UNIQUE,
    exam_name VARCHAR(128) NOT NULL,
    class_id INTEGER,
    knowledge_base_id INTEGER,
    process_template_id INTEGER,
    legacy_job_id INTEGER NOT NULL UNIQUE,
    instructions VARCHAR(2000),
    question_rounds INTEGER NOT NULL DEFAULT 5,
    passing_score INTEGER NOT NULL DEFAULT 60,
    publish_start DATETIME,
    publish_end DATETIME,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (class_id) REFERENCES school_class(id),
    FOREIGN KEY (knowledge_base_id) REFERENCES interview_knowledge_base(id),
    FOREIGN KEY (process_template_id) REFERENCES interview_process_template(id),
    FOREIGN KEY (legacy_job_id) REFERENCES recruitment_job(id)
);

CREATE TABLE IF NOT EXISTS school_exam_attempt (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    exam_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    process_id INTEGER NOT NULL UNIQUE,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at DATETIME,
    score_rate INTEGER,
    loss_rate INTEGER,
    ai_summary VARCHAR(4000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (exam_id, student_id),
    FOREIGN KEY (exam_id) REFERENCES school_exam(id),
    FOREIGN KEY (student_id) REFERENCES school_student(id),
    FOREIGN KEY (process_id) REFERENCES interview_process(id)
);

CREATE INDEX IF NOT EXISTS idx_school_class_major_name ON school_class(major_name);
CREATE INDEX IF NOT EXISTS idx_school_student_class_id ON school_student(class_id);
CREATE INDEX IF NOT EXISTS idx_school_exam_class_id ON school_exam(class_id);
CREATE INDEX IF NOT EXISTS idx_school_exam_status ON school_exam(status);
CREATE INDEX IF NOT EXISTS idx_school_exam_attempt_exam_id ON school_exam_attempt(exam_id);
CREATE INDEX IF NOT EXISTS idx_school_exam_attempt_student_id ON school_exam_attempt(student_id);

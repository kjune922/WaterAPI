ALTER TABLE ai_analysis
    ADD COLUMN review_status ENUM('PENDING_REVIEW','CONFIRMED') NOT NULL DEFAULT 'PENDING_REVIEW',
    ADD COLUMN confirmed_summary VARCHAR(500),
    ADD COLUMN confirmed_abnormality_type VARCHAR(100),
    ADD COLUMN confirmed_risk_level ENUM('NORMAL','CAUTION','WARNING'),
    ADD COLUMN confirmed_recommended_action VARCHAR(1000),
    ADD COLUMN confirmed_at DATETIME(6);

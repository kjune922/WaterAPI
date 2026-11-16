CREATE TABLE facilities (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    facility_type ENUM('PUMP','VALVE','PIPE','WATER_QUALITY_SENSOR') NOT NULL,
    location VARCHAR(100) NOT NULL
);
CREATE TABLE inspection_reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    facility_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    inspection_date DATE NOT NULL,
    processing_status ENUM('PENDING','IN_PROGRESS','COMPLETED') NOT NULL,
    CONSTRAINT fk_inspection_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    INDEX idx_inspection_status_date (processing_status, inspection_date)
);
CREATE TABLE ai_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inspection_report_id BIGINT NOT NULL,
    summary VARCHAR(500) NOT NULL,
    abnormality_type VARCHAR(100) NOT NULL,
    risk_level ENUM('NORMAL','CAUTION','WARNING') NOT NULL,
    recommended_action VARCHAR(1000) NOT NULL,
    analyzed_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_analysis_inspection UNIQUE (inspection_report_id),
    CONSTRAINT fk_analysis_inspection FOREIGN KEY (inspection_report_id) REFERENCES inspection_reports(id),
    INDEX idx_analysis_risk (risk_level)
);

ALTER TABLE inspection_reports
    ADD COLUMN form_version INT,
    ADD COLUMN recorded_type VARCHAR(32),
    ADD COLUMN inspector_name VARCHAR(50),
    ADD COLUMN notes VARCHAR(2000),
    ADD COLUMN action_taken VARCHAR(2000),
    ADD COLUMN input_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE inspection_answers (
    inspection_id BIGINT NOT NULL,
    item_order INT NOT NULL,
    code VARCHAR(40) NOT NULL,
    label VARCHAR(80) NOT NULL,
    result VARCHAR(24) NOT NULL,
    reason VARCHAR(500),
    PRIMARY KEY (inspection_id, item_order),
    UNIQUE (inspection_id, code),
    FOREIGN KEY (inspection_id) REFERENCES inspection_reports(id) ON DELETE CASCADE
);

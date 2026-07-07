CREATE TABLE IF NOT EXISTS short_code (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(255),
    original_url VARCHAR(65535) NOT NULL,
    alias VARCHAR(255) UNIQUE,
    is_protected BOOLEAN NOT NULL DEFAULT FALSE,
    is_password_auto_generated BOOLEAN,
    password VARCHAR(255),
    max_clicks INTEGER,
    unique_visitor_count INTEGER DEFAULT 0,
    expires_at TIMESTAMP,
    is_click_based BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    current_version_id BIGINT
);

CREATE TABLE IF NOT EXISTS url_version (
    id BIGSERIAL PRIMARY KEY,
    version_number INTEGER,
    original_url VARCHAR(65535),
    alias VARCHAR(255),
    is_protected BOOLEAN,
    is_password_auto_generated BOOLEAN,
    password VARCHAR(255),
    max_clicks INTEGER,
    expires_at TIMESTAMP,
    version_created_at TIMESTAMP,
    rollback_from_version INTEGER,
    is_rollback BOOLEAN DEFAULT FALSE,
    short_code_id BIGINT NOT NULL,
    CONSTRAINT fk_url_version_short_code FOREIGN KEY (short_code_id) REFERENCES short_code(id)
);

ALTER TABLE short_code
    ADD CONSTRAINT fk_short_code_current_version FOREIGN KEY (current_version_id) REFERENCES url_version(id);

CREATE INDEX IF NOT EXISTS idx_short_code_code ON short_code(short_code);
CREATE INDEX IF NOT EXISTS idx_short_code_alias ON short_code(alias);
CREATE INDEX IF NOT EXISTS idx_short_code_original ON short_code(original_url);
CREATE INDEX IF NOT EXISTS idx_url_version_short_code ON url_version(short_code_id);
CREATE INDEX IF NOT EXISTS idx_short_code_expires_at ON short_code(expires_at);
CREATE INDEX IF NOT EXISTS idx_short_code_is_click_based ON short_code(is_click_based) WHERE is_click_based = TRUE;

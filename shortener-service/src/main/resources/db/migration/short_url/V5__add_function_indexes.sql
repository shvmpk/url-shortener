-- Function-based indexes for LOWER() queries used by Spring Data JPA IgnoreCase methods
CREATE INDEX IF NOT EXISTS idx_short_code_code_lower ON short_code(LOWER(short_code));
CREATE INDEX IF NOT EXISTS idx_short_code_alias_lower ON short_code(LOWER(alias));
CREATE INDEX IF NOT EXISTS idx_short_code_original_lower ON short_code(LOWER(original_url));

-- Composite index for url_version lookups (covers FK + version ordering)
-- Replaces the less selective idx_url_version_short_code
DROP INDEX IF EXISTS idx_url_version_short_code;
CREATE INDEX IF NOT EXISTS idx_url_version_short_code_version ON url_version(short_code_id, version_number DESC);

-- Function-based index for LOWER(short_code) query (findByShortCodeIgnoreCase)
CREATE INDEX IF NOT EXISTS idx_analytics_short_code_lower ON analytics(LOWER(short_code));

-- BRIN index for time-series date-range scans (efficient for append-only data)
CREATE INDEX IF NOT EXISTS idx_analytics_access_date_brin ON analytics USING BRIN(access_date) WITH (pages_per_range = 32);

-- Drop standalone short_code B-tree index - superseded by LOWER index and composite (short_code, access_date)
DROP INDEX IF EXISTS idx_analytics_short_code;

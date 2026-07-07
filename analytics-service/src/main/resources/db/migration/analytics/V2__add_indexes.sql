CREATE INDEX IF NOT EXISTS idx_analytics_short_code ON analytics(short_code);
CREATE INDEX IF NOT EXISTS idx_analytics_access_date ON analytics(access_date);
CREATE INDEX IF NOT EXISTS idx_analytics_short_code_date ON analytics(short_code, access_date);

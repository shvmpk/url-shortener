CREATE TABLE IF NOT EXISTS short_url.id_checkpoint (
    id INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    last_value BIGINT NOT NULL
);

INSERT INTO short_url.id_checkpoint (id, last_value)
VALUES (1, 0)
ON CONFLICT (id) DO NOTHING;

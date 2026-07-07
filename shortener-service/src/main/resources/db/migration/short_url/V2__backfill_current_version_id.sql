UPDATE short_code sc
SET current_version_id = (
    SELECT uv.id
    FROM url_version uv
    WHERE uv.short_code_id = sc.id
    ORDER BY uv.version_number ASC
    LIMIT 1
)
WHERE sc.current_version_id IS NULL
  AND EXISTS (SELECT 1 FROM url_version uv WHERE uv.short_code_id = sc.id);

-- F-10: case-insensitive unique constraint on test.title.
-- Eliminates the dedup race window in TaskImportService: previously two
-- concurrent CSV imports could both pass the in-memory existingTitles
-- check (snapshotted from findAllTitlesLowercase before either commits)
-- and both insert rows with the same title, distinct test_id.
--
-- The service catches DataIntegrityViolationException from this constraint
-- and counts the conflicting row as 'skipped' rather than failing the
-- whole transaction.
--
-- Cleanup before constraint: collapse any pre-existing case-insensitive
-- duplicates onto the row with the smallest id (preserves the oldest
-- test_id reference; submission/result FKs point at test_id, not id).
DELETE FROM test
WHERE id IN (
    SELECT t.id
    FROM test t
    JOIN (
        SELECT LOWER(title) AS lowered, MIN(id) AS keep_id
        FROM test
        WHERE title IS NOT NULL
        GROUP BY LOWER(title)
        HAVING COUNT(*) > 1
    ) dup ON LOWER(t.title) = dup.lowered AND t.id <> dup.keep_id
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_test_title_lower
    ON test (LOWER(title))
    WHERE title IS NOT NULL;

-- CareConnect baseline migration.
-- Establishes the Flyway history for the schema. Feature tables arrive in
-- later migrations, one concern per file, never edited after release.
COMMENT ON SCHEMA public IS 'CareConnect clinical data schema';

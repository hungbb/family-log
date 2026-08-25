-- V1 — PostgreSQL extensions and the helpers that later migrations depend on.
--
-- Requires a role with rights to create extensions (superuser, or one granted pg_database_owner on
-- the target database). See the root README for the local setup.

-- gen_random_uuid() for database-side defaults, and digest()/crypt() for token hashing.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- unaccent(): fold Vietnamese diacritics so "Nguyễn" matches a search for "nguyen".
CREATE EXTENSION IF NOT EXISTS unaccent;

-- pg_trgm: trigram indexes for fuzzy name search.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- unaccent() itself is only STABLE, because it reads a dictionary that a superuser could change at
-- runtime. PostgreSQL therefore refuses it inside an index expression or a generated column.
--
-- This wrapper pins the dictionary and declares the result IMMUTABLE, which is what Phase 4's
-- generated `tsvector` search column needs. The pinning is the point: without the explicit
-- 'unaccent' regdictionary argument the lie would be unsafe.
CREATE OR REPLACE FUNCTION f_unaccent(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS $$
SELECT public.unaccent('public.unaccent'::regdictionary, $1)
$$;

COMMENT ON FUNCTION f_unaccent(text) IS
    'IMMUTABLE wrapper around unaccent() with a pinned dictionary, so it can be used in generated columns and index expressions.';

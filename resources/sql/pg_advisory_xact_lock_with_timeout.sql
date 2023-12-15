CREATE OR REPLACE FUNCTION __pg_try_advisory_xact_lock_with_timeout__(key bigint, timeout_sec bigint)
RETURNS boolean
LANGUAGE plpgsql
AS
$$
DECLARE
  ts text;
BEGIN
  ts := timeout_sec || 's';
  EXECUTE format('SET LOCAL lock_timeout = %L', ts);
  PERFORM pg_advisory_xact_lock(key);
  RETURN true;
EXCEPTION
  WHEN lock_not_available OR deadlock_detected THEN
    RETURN false;
END;
$$

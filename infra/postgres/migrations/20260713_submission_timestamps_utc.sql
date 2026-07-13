\set ON_ERROR_STOP on

-- 기존 TIMESTAMP 값은 한국 표준시로 기록되었으므로 실제 시각을 보존해 TIMESTAMPTZ로 변환합니다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'submissions'
          AND column_name = 'created_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE submissions
            ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
            USING created_at AT TIME ZONE 'Asia/Seoul';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'submissions'
          AND column_name = 'judged_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE submissions
            ALTER COLUMN judged_at TYPE TIMESTAMP WITH TIME ZONE
            USING judged_at AT TIME ZONE 'Asia/Seoul';
    END IF;
END
$$;

SELECT format('ALTER DATABASE %I SET timezone TO ''UTC''', current_database()) \gexec

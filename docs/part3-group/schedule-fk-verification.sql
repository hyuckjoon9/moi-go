SELECT
    rc.TABLE_NAME,
    rc.CONSTRAINT_NAME,
    rc.DELETE_RULE,
    kcu.COLUMN_NAME,
    kcu.REFERENCED_TABLE_NAME,
    kcu.REFERENCED_COLUMN_NAME
FROM information_schema.REFERENTIAL_CONSTRAINTS rc
JOIN information_schema.KEY_COLUMN_USAGE kcu
  ON kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
 AND kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
 AND kcu.TABLE_NAME = rc.TABLE_NAME
WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
  AND kcu.REFERENCED_TABLE_NAME = 'study_schedules'
  AND kcu.COLUMN_NAME = 'schedule_id'
ORDER BY rc.TABLE_NAME;

-- Expected DELETE_RULE values:
-- activity_records      RESTRICT
-- attendance_records    RESTRICT
-- attendance_responses  CASCADE

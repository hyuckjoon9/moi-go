ALTER TABLE attendance_responses
    ADD CONSTRAINT fk_attendance_responses_schedule
    FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
    ON DELETE CASCADE;

ALTER TABLE attendance_records
    ADD CONSTRAINT fk_attendance_records_schedule
    FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
    ON DELETE RESTRICT;

ALTER TABLE activity_records
    ADD CONSTRAINT fk_activity_records_schedule
    FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
    ON DELETE RESTRICT;

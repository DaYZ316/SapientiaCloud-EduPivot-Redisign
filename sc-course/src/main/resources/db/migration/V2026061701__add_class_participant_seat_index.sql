ALTER TABLE edu_class_participant
    ADD COLUMN IF NOT EXISTS seat_index INTEGER;

CREATE UNIQUE INDEX IF NOT EXISTS uq_edu_class_participant_session_seat
    ON edu_class_participant (session_id, seat_index)
    WHERE seat_index IS NOT NULL;

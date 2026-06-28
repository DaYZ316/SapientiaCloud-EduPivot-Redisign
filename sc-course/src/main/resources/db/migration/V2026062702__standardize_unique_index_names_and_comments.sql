DO $$
BEGIN
    IF to_regclass('uq_edu_class_participant_session_seat') IS NOT NULL
        AND to_regclass('uk_edu_class_participant_session_seat') IS NULL THEN
        ALTER INDEX uq_edu_class_participant_session_seat
            RENAME TO uk_edu_class_participant_session_seat;
    END IF;

    IF to_regclass('uq_edu_live_practice_group_session_order') IS NOT NULL
        AND to_regclass('uk_edu_live_practice_group_session_order') IS NULL THEN
        ALTER INDEX uq_edu_live_practice_group_session_order
            RENAME TO uk_edu_live_practice_group_session_order;
    END IF;

    IF to_regclass('uq_edu_live_practice_question_group_order') IS NOT NULL
        AND to_regclass('uk_edu_live_practice_question_group_order') IS NULL THEN
        ALTER INDEX uq_edu_live_practice_question_group_order
            RENAME TO uk_edu_live_practice_question_group_order;
    END IF;

    IF to_regclass('uq_edu_live_practice_submission_once') IS NOT NULL
        AND to_regclass('uk_edu_live_practice_submission_once') IS NULL THEN
        ALTER INDEX uq_edu_live_practice_submission_once
            RENAME TO uk_edu_live_practice_submission_once;
    END IF;
END $$;

COMMENT ON TABLE edu_course_file IS 'Course storage file binding';

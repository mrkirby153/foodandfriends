ALTER TABLE "schedule"
    ADD COLUMN "schedule_cadence_type" int default 0,
    ADD COLUMN "post_offset_days" int default 6,
    DROP COLUMN "post_day_of_week";
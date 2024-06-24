ALTER TABLE "event"
    ADD COLUMN "log_message_id" bigint default null;

ALTER TABLE "schedule"
    ADD COLUMN "log_channel_id" bigint default null;
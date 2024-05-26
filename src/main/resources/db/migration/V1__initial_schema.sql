CREATE TABLE data_store
(
    id            varchar(255) not null primary key,
    data_store_id varchar(255) not null,
    key           varchar(255) not null,
    value         bytea
);

CREATE INDEX "data_store_data_store_id" ON "data_store" ("data_store_id");
CREATE INDEX "data_store_data_store_id_key" ON "data_store" ("data_store_id", "key");

CREATE TABLE schedule_order
(
    id      varchar(255) not null primary key,
    "order" varchar(1024)         default '',
    current int          not null default 0
);

CREATE TABLE schedule
(
    id                varchar(255) not null primary key,
    post_day_of_week  int          default 0,
    post_time         varchar(255) not null,
    channel           bigint       not null,
    order_id          varchar(255) default null references schedule_order (id),
    event_day_of_week int          default 0,
    event_time        varchar(255) not null,
    message           text
);

CREATE TABLE person
(
    id         varchar(255) not null primary key,
    email      varchar(255),
    discord_id bigint
);

CREATE TABLE event
(
    id                 varchar(255) not null primary key,
    discord_message_id bigint,
    calendar_event_id  varchar(255),
    date               timestamp,
    schedule_id        varchar(255) references schedule (id)
)
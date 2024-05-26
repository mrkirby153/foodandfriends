CREATE TABLE data_store
(
    id            varchar(255) not null primary key,
    data_store_id varchar(255) not null,
    key           varchar(255) not null,
    value         bytea
);

CREATE INDEX "data_store_data_store_id" ON "data_store" ("data_store_id");
CREATE INDEX "data_store_data_store_id_key" ON "data_store" ("data_store_id", "key");

CREATE TABLE "order"
(
    id      varchar(255) not null primary key,
    "order" varchar(1024) default ''
);

CREATE TYPE dayOfWeek as ENUM ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY');

CREATE TABLE schedule
(
    id               varchar(255) not null primary key,
    post_day_of_week dayOfWeek default 'MONDAY',
    post_time        varchar(255) not null,
    channel          bigint       not null,
    order_id         varchar(255) not null references "order" (id)
);

CREATE TABLE person
(
    id         varchar(255) not null primary key,
    email      varchar(255),
    discord_id bigint
)
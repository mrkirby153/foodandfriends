CREATE TABLE emails
(
    id      int          not null primary key,
    user_id varchar(255) not null,
    email   varchar(255) not null
);

CREATE TABLE messages
(
    id         int          not null primary key,
    message_id varchar(255) not null,
    channel_id varchar(255) not null
);

CREATE TABLE data_store
(
    id            varchar(255) not null primary key,
    data_store_id varchar(255) not null,
    key           varchar(255) not null,
    value         bytea
);

CREATE INDEX "data_store_data_store_id" ON "data_store" ("data_store_id");
CREATE INDEX "data_store_data_store_id_key" ON "data_store" ("data_store_id", "key");

CREATE SEQUENCE hibernate_sequence START 1;
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

CREATE SEQUENCE hibernate_sequence START 1;
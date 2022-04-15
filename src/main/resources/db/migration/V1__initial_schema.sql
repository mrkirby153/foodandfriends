CREATE TABLE "event" (
    id int not null primary key,
    date timestamp not null,
    owner varchar(255) not null,
    attending_button varchar(255) not null
);

CREATE TABLE attendee (
    id int not null primary key,
    "user" varchar(255) not null,
    event_id int not null,
    foreign key (event_id) references event(id) ON DELETE CASCADE
);

CREATE TABLE emails (
    id int not null primary key,
    user_id varchar(255) not null,
    email varchar(255) not null
)
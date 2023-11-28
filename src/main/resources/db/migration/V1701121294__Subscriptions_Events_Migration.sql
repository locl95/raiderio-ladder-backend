create table subscriptions
(
    name varchar(128) not null primary key,
    state text not null default '{"status": "WAITING","version": 0,"time": "1995-09-14T12:34:56.789Z"}'
);

create sequence event_versions;

create table events
(
    version integer default NEXTVAL('event_versions') primary key,
    aggregate_root varchar(128) not null,
    data text not null
);

create index aggregate_root ON events (aggregate_root);

insert into subscriptions (name) values ('data-cache');
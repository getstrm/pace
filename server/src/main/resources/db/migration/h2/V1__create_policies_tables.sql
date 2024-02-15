create schema if not exists pace;

create table if not exists pace.data_policies
(
    id          varchar                                not null,
    platform_id varchar                                not null,
    title       varchar                                not null,
    description varchar,
    version     int                                    not null,
    created_at  timestamp with time zone default now() not null,
    updated_at  timestamp with time zone default now() not null,
    policy      json                                   not null,
    active      boolean                  default false not null,
    constraint data_policies_pk primary key (id, platform_id, version)
);

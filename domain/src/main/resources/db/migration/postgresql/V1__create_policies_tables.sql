create schema if not exists pace;
set search_path to pg_catalog,public,pace;

create
    extension if not exists "uuid-ossp" schema pace;

create table if not exists pace.data_policies
(
    id              varchar                   not null,
    platform_id     varchar                   not null,
    title           varchar                   not null,
    description     varchar,
    version         int                       not null,
    created_at      timestamptz default now() not null,
    updated_at      timestamptz default now() not null,
    policy          jsonb                     not null,
    active          boolean     default false not null,
    constraint data_policies_pk primary key (id, platform_id, version)
);

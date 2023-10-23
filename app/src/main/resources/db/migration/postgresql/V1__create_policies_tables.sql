create schema pace;
set search_path to pg_catalog,public,pace;

create
    extension if not exists "uuid-ossp" schema pace;

create table pace.data_policies
(
    id              varchar                   not null,
    title           varchar                   not null,
    description     varchar,
    version         varchar                   not null,
    organization_id varchar                   not null,
    created_at      timestamptz default now() not null,
    updated_at      timestamptz default now() not null,
    policy          jsonb                     not null,
    active          boolean     default false not null,
    constraint test_pk primary key (id, updated_at)
);


create table if not exists pace.global_transforms
(
    ref             varchar                   not null,
    description     varchar,
    created_at      timestamptz default now() not null,
    updated_at      timestamptz default now() not null,
    transform       jsonb                     not null,
    active          boolean     default false not null,
    constraint global_transforms_pk primary key (ref)
);

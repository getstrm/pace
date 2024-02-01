create table if not exists pace.global_transforms
(
    -- is equal to the identifier of the oneof transform
    -- can not freely chosen
    ref            varchar     not null,
    -- defines the type of oneof transform
    transform_type varchar     not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    transform      jsonb       not null,
    active         boolean     not null,
    constraint global_transforms_pk primary key (ref, transform_type)
);

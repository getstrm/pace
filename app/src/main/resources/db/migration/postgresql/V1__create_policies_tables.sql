CREATE SCHEMA data_policies;
SET search_path TO pg_catalog,public,data_policies;

CREATE
    EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA data_policies;

CREATE TABLE data_policies.policies
(
    id              varchar                   NOT NULL,
    title           varchar                   NOT NULL,
    description     varchar,
    version         varchar                   NOT NULL,
    organization_id varchar                   NOT NULL,
    created_at      timestamptz DEFAULT now() NOT NULL,
    updated_at      timestamptz DEFAULT now() NOT NULL,
    policy          jsonb                     NOT NULL,
    active          boolean     DEFAULT false NOT NULL,
    CONSTRAINT test_pk PRIMARY KEY (id, updated_at)
);

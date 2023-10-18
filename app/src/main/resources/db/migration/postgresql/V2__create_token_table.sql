-- permanent storage of tokens to speed up startup.
CREATE TABLE data_policies.processing_platform_tokens
(
    platform_id     varchar                   NOT NULL,
    access_token    varchar,
    refresh_token    varchar,
    expires_at      timestamp with time zone,
    refresh_token_expires_at      timestamp with time zone,
    CONSTRAINT processing_platform_tokens_pk PRIMARY KEY (platform_id)
);

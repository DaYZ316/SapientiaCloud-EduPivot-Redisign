CREATE TABLE IF NOT EXISTS auth_users
(
    id
    UUID
    PRIMARY
    KEY,
    email
    VARCHAR
(
    320
),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    display_name VARCHAR
(
    128
),
    avatar_url TEXT,
    locale VARCHAR
(
    32
),
    status VARCHAR
(
    32
) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
    );

CREATE TABLE IF NOT EXISTS auth_user_identities
(
    id
    UUID
    PRIMARY
    KEY,
    user_id
    UUID
    NOT
    NULL,
    provider
    VARCHAR
(
    32
) NOT NULL,
    provider_user_id VARCHAR
(
    191
) NOT NULL,
    provider_login VARCHAR
(
    191
),
    provider_email VARCHAR
(
    320
),
    provider_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    provider_display_name VARCHAR
(
    128
),
    provider_avatar_url TEXT,
    linked_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ,
    CONSTRAINT fk_auth_user_identities_user
    FOREIGN KEY
(
    user_id
) REFERENCES auth_users
(
    id
)
    ON DELETE CASCADE,
    CONSTRAINT uk_auth_user_identities_provider_user
    UNIQUE
(
    provider,
    provider_user_id
)
    );

CREATE INDEX IF NOT EXISTS idx_auth_user_identities_user_id
    ON auth_user_identities (user_id);

CREATE INDEX IF NOT EXISTS idx_auth_users_email
    ON auth_users (email);

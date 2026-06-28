ALTER TABLE auth_user_identities
    DROP CONSTRAINT IF EXISTS fk_auth_user_identities_user;

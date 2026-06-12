ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS avatar_file_id UUID;

CREATE INDEX IF NOT EXISTS idx_auth_users_avatar_file_id
    ON auth_users(avatar_file_id);

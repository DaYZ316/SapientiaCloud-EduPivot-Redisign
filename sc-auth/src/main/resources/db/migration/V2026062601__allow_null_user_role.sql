-- Allow OAuth users to finish role selection during onboarding.
ALTER TABLE auth_users
    ALTER COLUMN role DROP DEFAULT,
ALTER
COLUMN role DROP
NOT NULL;

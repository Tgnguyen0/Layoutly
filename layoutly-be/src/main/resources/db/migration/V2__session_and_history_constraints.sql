ALTER TABLE user_sessions ADD CONSTRAINT sessions_kind CHECK (kind IN ('WEB', 'BRIDGE'));
ALTER TABLE project_assets ADD CONSTRAINT assets_nonnegative_size CHECK (byte_size >= 0);
ALTER TABLE exports ADD CONSTRAINT exports_nonnegative_size CHECK (byte_size >= 0);
ALTER TABLE exports ADD CONSTRAINT exports_format CHECK (format IN ('HTML', 'REACT'));
CREATE INDEX sessions_expiry ON user_sessions(expires_at);
CREATE INDEX reset_tokens_user_expiry ON password_reset_tokens(user_id, expires_at);

-- Se păstrează numai identificatorul aleatoriu JWT, niciodată tokenul Bearer.
-- Tokenurile emise înainte de această migrare necesită o autentificare nouă.
CREATE TABLE user_sessions (
    id varchar(36) PRIMARY KEY,
    user_id varchar(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_version integer NOT NULL,
    device varchar(80) NOT NULL,
    created_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > created_at)
);
CREATE INDEX idx_sessions_user ON user_sessions(user_id, created_at DESC);
CREATE INDEX idx_sessions_expiry ON user_sessions(expires_at);

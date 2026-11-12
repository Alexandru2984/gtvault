-- Un singur premiu țintă per cont. Nu se rezervă și nu se modifică AP sau stoc.
CREATE TABLE saving_goals (
    user_id varchar(255) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    reward_id varchar(255) NOT NULL REFERENCES rewards(id) ON DELETE CASCADE,
    selected_at timestamptz NOT NULL,
    last_checked_at timestamptz,
    notified_at timestamptz
);
CREATE INDEX idx_goals_pending ON saving_goals(last_checked_at NULLS FIRST, selected_at, user_id)
    WHERE notified_at IS NULL;

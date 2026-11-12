-- Se salvează numai răspunsuri trimise, împreună cu întrebarea și variantele văzute atunci.
CREATE TABLE quiz_attempts (
    user_id varchar(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_date date NOT NULL,
    question_id text NOT NULL,
    category text NOT NULL,
    question text NOT NULL,
    options jsonb NOT NULL CHECK (jsonb_typeof(options) = 'array' AND jsonb_array_length(options) BETWEEN 2 AND 10),
    selected_index integer NOT NULL CHECK (selected_index >= 0 AND selected_index < jsonb_array_length(options)),
    correct_index integer NOT NULL CHECK (correct_index >= 0 AND correct_index < jsonb_array_length(options)),
    explanation text NOT NULL,
    reward_points integer NOT NULL CHECK (reward_points >= 0),
    answered_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, game_date),
    CHECK (selected_index = correct_index OR reward_points = 0)
);

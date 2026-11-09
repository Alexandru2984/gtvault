-- V5: comunitate – recenzii verificate, voturi „util”, raportari, wishlist, Tech Wordle, profil public.

-- ---------------------------------------------------------------- profil public
ALTER TABLE users ADD COLUMN public_profile boolean DEFAULT true NOT NULL;
ALTER TABLE users ADD COLUMN bio character varying(160);

-- ---------------------------------------------------------------- recenzii
-- Numele/avatarul autorului erau copiate in recenzie si ramaneau vechi dupa schimbarea profilului;
-- acum se citesc mereu din users.
ALTER TABLE product_reviews DROP COLUMN user_name;
ALTER TABLE product_reviews DROP COLUMN user_avatar;
ALTER TABLE product_reviews ADD COLUMN updated_at timestamp with time zone;
ALTER TABLE product_reviews ADD COLUMN helpful_count integer DEFAULT 0 NOT NULL;
ALTER TABLE product_reviews ADD COLUMN hidden boolean DEFAULT false NOT NULL;
UPDATE product_reviews SET created_at = now() WHERE created_at IS NULL;
ALTER TABLE product_reviews ALTER COLUMN created_at SET NOT NULL;
-- O singura recenzie per utilizator si produs (pastram cea mai recenta)
DELETE FROM product_reviews a
USING product_reviews b
WHERE a.reward_id = b.reward_id AND a.user_id = b.user_id
  AND (a.created_at < b.created_at OR (a.created_at = b.created_at AND a.id < b.id));
ALTER TABLE product_reviews ADD CONSTRAINT ux_review_user_reward UNIQUE (reward_id, user_id);
ALTER TABLE product_reviews ADD CONSTRAINT product_reviews_comment_check CHECK (char_length(comment) BETWEEN 1 AND 1000);
ALTER TABLE product_reviews ADD CONSTRAINT product_reviews_helpful_check CHECK (helpful_count >= 0);
CREATE INDEX idx_reviews_reward_date ON product_reviews (reward_id, created_at DESC);
CREATE INDEX idx_reviews_user ON product_reviews (user_id);
CREATE INDEX idx_reviews_date ON product_reviews (created_at DESC);

CREATE TABLE review_votes (
    review_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT review_votes_pkey PRIMARY KEY (review_id, user_id),
    CONSTRAINT fk_review_votes_review FOREIGN KEY (review_id) REFERENCES product_reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_votes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_review_votes_user ON review_votes (user_id);

CREATE TABLE review_reports (
    review_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    reason character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT review_reports_pkey PRIMARY KEY (review_id, user_id),
    CONSTRAINT fk_review_reports_review FOREIGN KEY (review_id) REFERENCES product_reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reports_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_review_reports_user ON review_reports (user_id);

-- Recompensa pentru recenzie se acorda o singura data per produs, chiar daca recenzia e stearsa si rescrisa.
CREATE TABLE review_rewards (
    user_id character varying(255) NOT NULL,
    reward_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT review_rewards_pkey PRIMARY KEY (user_id, reward_id),
    CONSTRAINT fk_review_rewards_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_rewards_reward FOREIGN KEY (reward_id) REFERENCES rewards(id) ON DELETE CASCADE
);
-- Recenziile existente au fost deja „platite” (nu mai pot genera AP)
INSERT INTO review_rewards (user_id, reward_id, created_at)
SELECT user_id, reward_id, created_at FROM product_reviews
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- wishlist
CREATE TABLE wishlist_items (
    user_id character varying(255) NOT NULL,
    reward_id character varying(255) NOT NULL,
    added_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT wishlist_items_pkey PRIMARY KEY (user_id, reward_id),
    CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_reward FOREIGN KEY (reward_id) REFERENCES rewards(id) ON DELETE CASCADE
);
CREATE INDEX idx_wishlist_reward ON wishlist_items (reward_id);

-- ---------------------------------------------------------------- Tech Wordle
CREATE TABLE wordle_games (
    user_id character varying(255) NOT NULL,
    game_date character varying(16) NOT NULL,
    guesses character varying(64) DEFAULT '' NOT NULL,
    status character varying(8) DEFAULT 'PLAYING' NOT NULL,
    reward integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT wordle_games_pkey PRIMARY KEY (user_id, game_date),
    CONSTRAINT wordle_games_status_check CHECK (status IN ('PLAYING', 'WON', 'LOST')),
    CONSTRAINT wordle_games_reward_check CHECK (reward >= 0),
    CONSTRAINT fk_wordle_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_wordle_date_status ON wordle_games (game_date, status);

-- ---------------------------------------------------------------- feed comunitate
CREATE INDEX IF NOT EXISTS idx_user_badges_unlocked ON user_badges (unlocked_at DESC);

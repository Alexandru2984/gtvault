-- V3: economia de puncte (ghilde cu cooldown, misiuni unice per perioada)

ALTER TABLE users ADD COLUMN guild_changed_at timestamp with time zone;
CREATE INDEX idx_users_guild ON users (guild_id);
CREATE INDEX idx_users_streak ON users (streak_days DESC);

-- Misiuni: eliminam eventualele duplicate create de cereri concurente (pastram una revendicata, daca exista)
DELETE FROM user_quests a
USING user_quests b
WHERE a.user_id = b.user_id
  AND a.quest_key = b.quest_key
  AND a.quest_type = b.quest_type
  AND a.quest_date = b.quest_date
  AND (b.claimed, b.id) > (a.claimed, a.id);
CREATE UNIQUE INDEX ux_user_quests_period ON user_quests (user_id, quest_type, quest_key, quest_date);

CREATE INDEX idx_daily_counters_date ON user_daily_counters (counter_date);
CREATE INDEX idx_proposal_votes_user ON proposal_votes (user_id);

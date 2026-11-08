-- Rulare O SINGURA DATA, ca superuser, inainte de primul deploy cu Flyway:
--   sudo -u postgres psql -d gtshop -f deploy/db/00-transfer-ownership.sql
-- Motiv: o parte din tabele au fost create manual de `postgres`, iar migratiile
-- Flyway ruleaza cu `gtshop_user`, care nu poate face ALTER pe tabele straine.
ALTER TABLE public.promo_codes        OWNER TO gtshop_user;
ALTER TABLE public.point_transactions OWNER TO gtshop_user;
ALTER TABLE public.user_badges        OWNER TO gtshop_user;
ALTER TABLE public.product_reviews    OWNER TO gtshop_user;
ALTER TABLE public.user_quests        OWNER TO gtshop_user;
ALTER TABLE public.reward_proposals   OWNER TO gtshop_user;
ALTER TABLE public.proposal_votes     OWNER TO gtshop_user;

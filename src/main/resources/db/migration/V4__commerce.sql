-- V4: magazin autoritar pe server (cos, comenzi cu livrare, coduri promo per utilizator, flash deals reale)

-- ---------------------------------------------------------------- rewards
ALTER TABLE rewards ADD COLUMN active boolean DEFAULT true NOT NULL;
ALTER TABLE rewards ADD COLUMN fulfillment character varying(16) DEFAULT 'DIGITAL' NOT NULL;
ALTER TABLE rewards ADD COLUMN max_per_order integer DEFAULT 5 NOT NULL;
ALTER TABLE rewards ADD COLUMN min_tier character varying(16);
ALTER TABLE rewards ADD COLUMN created_at timestamp with time zone DEFAULT now() NOT NULL;

UPDATE rewards SET fulfillment = 'PHYSICAL' WHERE category = 'Premii fizice';
-- „Badge Hero” promite o insigna in profil: se livreaza direct in aplicatie, o singura data
UPDATE rewards SET fulfillment = 'BADGE', max_per_order = 1 WHERE id = 'rew-badge';
-- „Lucky Crate” promitea sanse la alte premii, dar nu era implementat (primeai doar un cod fara valoare).
-- Il dezactivam; cuferele reale sunt in sectiunea Activitati.
UPDATE rewards SET active = false WHERE id = 'rew-lucky';
UPDATE rewards SET is_new = false WHERE is_new IS NULL;
ALTER TABLE rewards ALTER COLUMN is_new SET DEFAULT false;
ALTER TABLE rewards ALTER COLUMN is_new SET NOT NULL;
UPDATE rewards SET stock_count = 0 WHERE stock_count < 0;
UPDATE rewards SET in_stock = (stock_count > 0);
ALTER TABLE rewards ADD CONSTRAINT rewards_stock_check CHECK (stock_count >= 0);
ALTER TABLE rewards ADD CONSTRAINT rewards_price_check CHECK (price > 0);
ALTER TABLE rewards ADD CONSTRAINT rewards_max_per_order_check CHECK (max_per_order BETWEEN 1 AND 99);
CREATE INDEX idx_rewards_active_category ON rewards (active, category);

-- ---------------------------------------------------------------- comenzi
CREATE TABLE orders (
    id character varying(32) NOT NULL,
    user_id character varying(255) NOT NULL,
    status character varying(16) NOT NULL,
    subtotal integer NOT NULL,
    flash_discount integer DEFAULT 0 NOT NULL,
    tier_discount integer DEFAULT 0 NOT NULL,
    promo_discount integer DEFAULT 0 NOT NULL,
    total integer NOT NULL,
    tier_code character varying(16),
    promo_code character varying(64),
    idempotency_key character varying(64),
    requires_shipping boolean DEFAULT false NOT NULL,
    shipping_name character varying(80),
    shipping_phone character varying(20),
    shipping_address character varying(200),
    shipping_city character varying(80),
    shipping_county character varying(40),
    shipping_postal_code character varying(10),
    notes character varying(300),
    tracking_code character varying(64),
    admin_note character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT orders_pkey PRIMARY KEY (id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ux_orders_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT orders_total_check CHECK (total >= 0 AND subtotal >= 0)
);
CREATE INDEX idx_orders_user_date ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_date ON orders (status, created_at DESC);

-- ---------------------------------------------------------------- flash deals
CREATE TABLE flash_deals (
    id character varying(32) NOT NULL,
    reward_id character varying(255) NOT NULL,
    discount_percent integer NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    max_quantity integer NOT NULL,
    sold_quantity integer DEFAULT 0 NOT NULL,
    per_user_limit integer DEFAULT 2 NOT NULL,
    auto_generated boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT flash_deals_pkey PRIMARY KEY (id),
    CONSTRAINT fk_flash_reward FOREIGN KEY (reward_id) REFERENCES rewards(id) ON DELETE CASCADE,
    CONSTRAINT flash_percent_check CHECK (discount_percent BETWEEN 5 AND 80),
    CONSTRAINT flash_window_check CHECK (ends_at > starts_at),
    CONSTRAINT flash_quantity_check CHECK (max_quantity > 0 AND sold_quantity >= 0 AND sold_quantity <= max_quantity),
    CONSTRAINT flash_user_limit_check CHECK (per_user_limit > 0)
);
CREATE UNIQUE INDEX ux_flash_reward_start ON flash_deals (reward_id, starts_at);
CREATE INDEX idx_flash_window ON flash_deals (starts_at, ends_at);

-- ---------------------------------------------------------------- linii de comanda (purchase_records)
ALTER TABLE purchase_records ADD COLUMN order_id character varying(32);
ALTER TABLE purchase_records ADD COLUMN reward_id character varying(255);
ALTER TABLE purchase_records ADD COLUMN quantity integer DEFAULT 1 NOT NULL;
ALTER TABLE purchase_records ADD COLUMN unit_price integer;
ALTER TABLE purchase_records ADD COLUMN fulfillment character varying(16) DEFAULT 'DIGITAL' NOT NULL;
ALTER TABLE purchase_records ADD COLUMN flash_deal_id character varying(32);
ALTER TABLE purchase_records ADD COLUMN flash_units integer DEFAULT 0 NOT NULL;
ALTER TABLE purchase_records ADD COLUMN redeemed_at timestamp with time zone;

DELETE FROM purchase_records WHERE user_id IS NULL;
UPDATE purchase_records SET purchase_date = now() WHERE purchase_date IS NULL;
UPDATE purchase_records SET status = 'FULFILLED' WHERE status IS NULL OR status = 'Completed';
UPDATE purchase_records p SET reward_id = r.id, fulfillment = r.fulfillment
FROM rewards r WHERE p.reward_id IS NULL AND r.name = p.reward_name;
UPDATE purchase_records SET unit_price = points_spent WHERE unit_price IS NULL;

-- Coduri voucher duplicate (generate anterior din fragmente UUID) -> le facem unice inainte de index
UPDATE purchase_records p SET voucher_code = p.voucher_code || '-' || upper(substr(md5(p.id), 1, 4))
FROM (SELECT id, row_number() OVER (PARTITION BY voucher_code ORDER BY id) AS rn
      FROM purchase_records WHERE voucher_code IS NOT NULL) d
WHERE p.id = d.id AND d.rn > 1;

-- Cumparaturile vechi devin comenzi finalizate (cate una per linie, ca istoricul sa ramana complet)
INSERT INTO orders (id, user_id, status, subtotal, total, created_at, updated_at)
SELECT 'ORD-L' || upper(substr(md5(id), 1, 10)), user_id, 'FULFILLED', points_spent, points_spent, purchase_date, purchase_date
FROM purchase_records WHERE order_id IS NULL;
UPDATE purchase_records SET order_id = 'ORD-L' || upper(substr(md5(id), 1, 10)) WHERE order_id IS NULL;

ALTER TABLE purchase_records ALTER COLUMN order_id SET NOT NULL;
ALTER TABLE purchase_records ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE purchase_records ADD CONSTRAINT fk_purchase_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;
ALTER TABLE purchase_records DROP CONSTRAINT fkg2j34t93r8upvoch5c98y0t3t;
ALTER TABLE purchase_records ADD CONSTRAINT fk_purchase_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE purchase_records ADD CONSTRAINT fk_purchase_flash FOREIGN KEY (flash_deal_id) REFERENCES flash_deals(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX ux_purchase_voucher ON purchase_records (voucher_code) WHERE voucher_code IS NOT NULL;
CREATE INDEX idx_purchase_order ON purchase_records (order_id);
CREATE INDEX idx_purchase_user_reward ON purchase_records (user_id, reward_id);
CREATE INDEX idx_purchase_reward_date ON purchase_records (reward_id, purchase_date);
CREATE INDEX idx_purchase_flash_user ON purchase_records (flash_deal_id, user_id);

-- ---------------------------------------------------------------- cos (doar produs + cantitate; pretul vine mereu din catalog)
DELETE FROM cart_items WHERE user_id IS NULL OR reward_id IS NULL OR quantity <= 0;
DELETE FROM cart_items c WHERE NOT EXISTS (SELECT 1 FROM rewards r WHERE r.id = c.reward_id);
UPDATE cart_items c SET quantity = d.total
FROM (SELECT user_id, reward_id, min(id) AS keep_id, sum(quantity) AS total
      FROM cart_items GROUP BY user_id, reward_id HAVING count(*) > 1) d
WHERE c.id = d.keep_id;
DELETE FROM cart_items c USING cart_items k
WHERE c.user_id = k.user_id AND c.reward_id = k.reward_id AND c.id > k.id;
UPDATE cart_items SET quantity = 99 WHERE quantity > 99;

ALTER TABLE cart_items ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE cart_items ALTER COLUMN reward_id SET NOT NULL;
ALTER TABLE cart_items ADD COLUMN added_at timestamp with time zone DEFAULT now() NOT NULL;
ALTER TABLE cart_items ADD CONSTRAINT ux_cart_user_reward UNIQUE (user_id, reward_id);
ALTER TABLE cart_items ADD CONSTRAINT cart_quantity_check CHECK (quantity BETWEEN 1 AND 99);
ALTER TABLE cart_items DROP CONSTRAINT fk709eickf3kc0dujx3ub9i7btf;
ALTER TABLE cart_items ADD CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE cart_items ADD CONSTRAINT fk_cart_reward FOREIGN KEY (reward_id) REFERENCES rewards(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------- coduri promo
ALTER TABLE promo_codes ADD COLUMN description character varying(140);
ALTER TABLE promo_codes ADD COLUMN max_discount integer;
ALTER TABLE promo_codes ADD COLUMN created_at timestamp with time zone DEFAULT now() NOT NULL;
UPDATE promo_codes SET discount_percent = 90 WHERE discount_percent > 90;
UPDATE promo_codes SET discount_percent = 0 WHERE discount_percent < 0;
UPDATE promo_codes SET discount_points = 0 WHERE discount_points < 0;
ALTER TABLE promo_codes ADD CONSTRAINT promo_percent_check CHECK (discount_percent BETWEEN 0 AND 90);
ALTER TABLE promo_codes ADD CONSTRAINT promo_points_check CHECK (discount_points >= 0);
CREATE UNIQUE INDEX ux_promo_code_lower ON promo_codes (lower(code));

-- Fiecare cod poate fi folosit o singura data de acelasi utilizator
CREATE TABLE promo_redemptions (
    id bigint GENERATED BY DEFAULT AS IDENTITY,
    promo_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    order_id character varying(32),
    discount integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT promo_redemptions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_redemption_promo FOREIGN KEY (promo_id) REFERENCES promo_codes(id) ON DELETE CASCADE,
    CONSTRAINT fk_redemption_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_redemption_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,
    CONSTRAINT ux_promo_user UNIQUE (promo_id, user_id)
);

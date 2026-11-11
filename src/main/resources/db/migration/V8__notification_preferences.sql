-- Lipsa unei preferințe explicite păstrează comportamentul conturilor existente.
CREATE TABLE notification_preferences (
    user_id varchar(255) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    wishlist_sales boolean NOT NULL DEFAULT true,
    wishlist_stock boolean NOT NULL DEFAULT true,
    progress boolean NOT NULL DEFAULT true,
    community boolean NOT NULL DEFAULT true,
    announcements boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL
);

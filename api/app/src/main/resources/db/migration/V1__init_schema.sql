-- BearWithMe v1 schema (spec: Data model). Owners: auth = users; card = cards, tags, card_tags;
-- review = review_states, review_logs. Timestamps are UTC (timestamptz); due_date is a local
-- date in Asia/Ho_Chi_Minh.

-- auth ---------------------------------------------------------------------------------------

CREATE TABLE users (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      varchar(50)  NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username)
);

-- card ---------------------------------------------------------------------------------------

CREATE TABLE cards (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint        NOT NULL REFERENCES users (id),
    term       varchar(200)  NOT NULL,
    meaning    varchar(500)  NOT NULL,
    example    varchar(1000),
    note       varchar(1000),
    created_at timestamptz   NOT NULL DEFAULT now(),
    updated_at timestamptz   NOT NULL DEFAULT now(),
    -- FR-C2: the application trims; the database rejects untrimmed or blank terms
    CONSTRAINT ck_cards_term_trimmed CHECK (term = btrim(term) AND term <> '')
);

-- FR-C2: unique per user, case-insensitive
CREATE UNIQUE INDEX uq_cards_user_term ON cards (user_id, lower(term));
-- FR-C4: list sorted by created date
CREATE INDEX ix_cards_user_created ON cards (user_id, created_at);

CREATE TABLE tags (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint      NOT NULL REFERENCES users (id),
    name    varchar(50) NOT NULL,
    CONSTRAINT uq_tags_user_name UNIQUE (user_id, name),
    -- FR-C3: lowercase kebab-case
    CONSTRAINT ck_tags_name_kebab CHECK (name ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE card_tags (
    card_id bigint NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    tag_id  bigint NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (card_id, tag_id)
);

CREATE INDEX ix_card_tags_tag ON card_tags (tag_id);

-- review -------------------------------------------------------------------------------------

CREATE TABLE review_states (
    card_id          bigint       PRIMARY KEY REFERENCES cards (id) ON DELETE CASCADE,
    user_id          bigint       NOT NULL REFERENCES users (id),
    ease_factor      numeric(4,2) NOT NULL DEFAULT 2.50,
    interval_days    int          NOT NULL DEFAULT 0,
    repetitions      int          NOT NULL DEFAULT 0,
    -- NULL while the card is new (never reviewed), FR-C5
    due_date         date,
    last_reviewed_at timestamptz,
    CONSTRAINT ck_review_states_ease CHECK (ease_factor >= 1.30),
    CONSTRAINT ck_review_states_interval CHECK (interval_days >= 0),
    CONSTRAINT ck_review_states_repetitions CHECK (repetitions >= 0)
);

-- FR-R1: due queue, oldest due first
CREATE INDEX ix_review_states_user_due ON review_states (user_id, due_date);

CREATE TABLE review_logs (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    card_id           bigint       NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    user_id           bigint       NOT NULL REFERENCES users (id),
    -- 1 = Again, 2 = Hard, 3 = Good, 4 = Easy (spec: Scheduling rules)
    grade             smallint     NOT NULL,
    ease_factor_after numeric(4,2) NOT NULL,
    interval_after    int          NOT NULL,
    reviewed_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_review_logs_grade CHECK (grade BETWEEN 1 AND 4)
);

-- FR-D1, FR-D2: reviewed today, streak
CREATE INDEX ix_review_logs_user_reviewed ON review_logs (user_id, reviewed_at);
-- FR-C6: cascade delete by card
CREATE INDEX ix_review_logs_card ON review_logs (card_id);

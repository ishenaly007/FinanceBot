CREATE TABLE users
(
    id             BIGINT PRIMARY KEY,
    language       VARCHAR(2) DEFAULT 'ru',
    currency       VARCHAR(3) DEFAULT 'KGS',
    is_premium     BOOLEAN    DEFAULT FALSE,
    trial_end_date DATE,
    stats_time     TIME       DEFAULT '20:00:00',
    created_at     TIMESTAMP  DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories
(
    id         SERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES users (id),
    name       VARCHAR(50),
    type       VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_category UNIQUE (user_id, name, type)
);

CREATE TABLE transactions
(
    id          SERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users (id),
    type        VARCHAR(10),
    amount      DECIMAL(10, 2),
    category_id INTEGER REFERENCES categories (id),
    comment     TEXT,
    date        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
healthcheck project — interactive file tree with code viewer
-- Auto-runs on first postgres container start
-- (only when the data volume is empty)

CREATE TABLE IF NOT EXISTS users (
    id         SERIAL       PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    age        INT          NOT NULL
    );

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(60) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_app_users_username UNIQUE (username),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'RECEPCIONISTA'))
);

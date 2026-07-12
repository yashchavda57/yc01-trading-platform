 CREATE TABLE users (
      id             BIGSERIAL    PRIMARY KEY,
      email          VARCHAR(255) UNIQUE NOT NULL,
      password_hash  VARCHAR(255) NOT NULL,
      first_name     VARCHAR(100),
      last_name      VARCHAR(100),
      role           VARCHAR(50)  NOT NULL DEFAULT 'USER',
      kyc_status     VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
      enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
      email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
      version        BIGINT       NOT NULL DEFAULT 0,
      created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
      updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
  );

  CREATE INDEX idx_users_email ON users(email);
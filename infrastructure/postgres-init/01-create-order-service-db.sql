-- Runs only when the postgres container initializes an EMPTY data volume
-- (the official postgres image only executes docker-entrypoint-initdb.d
-- scripts on first boot). Gives order-service its own database on the same
-- Postgres instance as user-service, rather than sharing trading_users.
CREATE DATABASE trading_orders OWNER trading_user;

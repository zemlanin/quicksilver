CREATE TABLE auth_tokens
(
  id serial primary key NOT NULL,
  hash character varying(79) NOT NULL,
  email character varying(100) NOT NULL,
  date_created timestamp without time zone default (now() at time zone 'utc')
);

CREATE TABLE users
(
  id serial primary key NOT NULL,
  email character varying(100) UNIQUE NOT NULL,
  date_created timestamp without time zone default (now() at time zone 'utc')
);

INSERT INTO users (id, email) VALUES (1, "bwd@example.com")

CREATE TABLE sessions
(
  id character varying(30) UNIQUE NOT NULL,
  user_id INTEGER REFERENCES users (id),
  date_created timestamp without time zone default (now() at time zone 'utc')
);

ALTER TABLE widgets
  ADD user_id INTEGER NOT NULL DEFAULT 1,
  ADD FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE widgets
  ALTER user_id DROP DEFAULT;

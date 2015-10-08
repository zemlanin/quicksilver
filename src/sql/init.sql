CREATE TABLE messages
(
  id serial primary key NOT NULL,
  date_created timestamp without time zone default (now() at time zone 'utc'),
  author character varying(100),
  type character varying(30) NOT NULL,
  text character varying(1000) NOT NULL
);

CREATE TABLE slack_tokens
(
  id serial primary key NOT NULL,
  token character varying(100),
  date_created timestamp without time zone default (now() at time zone 'utc')
);

CREATE TYPE widgets_type AS ENUM ('random-text');

CREATE TABLE widgets
(
  id serial primary key NOT NULL,
  type widgets_type NOT NULL,
  source_data json,
  date_created timestamp without time zone default (now() at time zone 'utc')
);

ALTER TABLE messages
  ALTER type DROP NOT NULL,
  ADD widget_id INTEGER,
  ADD FOREIGN KEY (widget_id) REFERENCES widgets(id);

ALTER TABLE widgets
  ADD title character varying(100) NOT NULL default '';

ALTER TYPE widgets_type ADD VALUE 'static-text';
ALTER TYPE widgets_type ADD VALUE 'periodic-text';

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

CREATE TABLE sessions
(
  id character varying(30) UNIQUE NOT NULL,
  user_id INTEGER REFERENCES users (id),
  date_created timestamp without time zone default (now() at time zone 'utc')
);

ALTER TABLE widgets
  ADD user_id INTEGER NOT NULL,
  ADD FOREIGN KEY (user_id) REFERENCES users(id);

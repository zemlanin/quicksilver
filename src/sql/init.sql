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
-- ALTER TYPE widgets_type ADD VALUE 'random-text';

CREATE TABLE widgets
(
  id serial primary key NOT NULL,
  type widgets_type NOT NULL,
  source_data json,
  date_created timestamp without time zone default (now() at time zone 'utc')
);

ALTER TABLE messages
  ALTER type DROP NOT NULL
  ADD widget_id INTEGER,
  ADD FOREIGN KEY (widget_id) REFERENCES widgets(id);

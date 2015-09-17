CREATE TABLE messages
(
  id serial primary key NOT NULL,
  date_created timestamp without time zone default (now() at time zone 'utc'),
  author character varying(100),
  type character varying(30) NOT NULL,
  text character varying(1000) NOT NULL
);

INSERT INTO messages ("author", "text", "type") VALUES
  ('a.verinov', 'something', 'uno');

CREATE TABLE slack_tokens
(
  id serial primary key NOT NULL,
  token character varying(100),
  date_created timestamp without time zone default (now() at time zone 'utc')
);

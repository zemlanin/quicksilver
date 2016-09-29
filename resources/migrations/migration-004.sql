CREATE TABLE teams
(
  id serial primary key NOT NULL,
  date_created timestamp without time zone default (now() at time zone 'utc'),
  slack_id character varying(255) NOT NULL,
  slack_token character varying(255) NOT NULL,
  authors character varying(255)[] NOT NULL
);

INSERT INTO teams (id, slack_id, slack_token, authors)
  VALUES (1, 'slack_id', 'slack_token', '{"user1","user2"}');

ALTER TABLE widgets
  ADD team_id INTEGER NOT NULL DEFAULT 1,
  ADD FOREIGN KEY (team_id) REFERENCES teams(id);

ALTER TABLE widgets
  ALTER team_id DROP DEFAULT;

-- replace random-text values' format from {[text]: [chance]}
--  to {"text": [text], "chance": [chance]}

UPDATE widgets
  SET source_data = regexp_replace(
    regexp_replace(
      regexp_replace(
        source_data::text,
        '("[^"]+"):\s+(\d+),?',
        '{"text": \1, "chance": \2},',
        'g'
      ), '{{', '[{'
    ), ',}', ']'
  )::json
WHERE type = 'random-text';

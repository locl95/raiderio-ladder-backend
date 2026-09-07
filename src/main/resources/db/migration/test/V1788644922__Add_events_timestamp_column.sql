ALTER TABLE events ADD COLUMN timestamp text NOT NULL DEFAULT '2026-09-05T21:48:46.000Z';

CREATE INDEX events_timestamp ON events (timestamp);

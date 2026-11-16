-- DJ-Cloud cross-device synchronization schema
-- All writes and reads are performed by authenticated server endpoints.

CREATE TABLE IF NOT EXISTS public.dj_devices (
  user_id text NOT NULL,
  device_id uuid NOT NULL,
  name text NOT NULL,
  platform text NOT NULL CHECK (platform IN ('android', 'web', 'desktop')),
  user_agent text,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, device_id)
);

CREATE TABLE IF NOT EXISTS public.dj_playback_state (
  user_id text PRIMARY KEY,
  active_device_id uuid,
  current_track jsonb,
  queue jsonb NOT NULL DEFAULT '[]'::jsonb,
  is_playing boolean NOT NULL DEFAULT false,
  position_ms bigint NOT NULL DEFAULT 0 CHECK (position_ms >= 0),
  duration_ms bigint NOT NULL DEFAULT 0 CHECK (duration_ms >= 0),
  playback_mode text NOT NULL DEFAULT 'NORMAL'
    CHECK (playback_mode IN ('NORMAL', 'REPEAT_ONE', 'REPEAT_ALL', 'SHUFFLE')),
  revision bigint NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.dj_library_tracks (
  user_id text NOT NULL,
  track_id text NOT NULL,
  track jsonb NOT NULL,
  is_favorite boolean NOT NULL DEFAULT false,
  added_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, track_id)
);

CREATE TABLE IF NOT EXISTS public.dj_playlists (
  user_id text NOT NULL,
  playlist_id text NOT NULL,
  name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, playlist_id)
);

CREATE TABLE IF NOT EXISTS public.dj_playlist_items (
  user_id text NOT NULL,
  playlist_id text NOT NULL,
  track_id text NOT NULL,
  position integer NOT NULL DEFAULT 0,
  added_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, playlist_id, track_id),
  FOREIGN KEY (user_id, playlist_id)
    REFERENCES public.dj_playlists(user_id, playlist_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.dj_device_commands (
  user_id text NOT NULL,
  command_id uuid NOT NULL DEFAULT gen_random_uuid(),
  target_device_id uuid NOT NULL,
  source_device_id uuid NOT NULL,
  command_type text NOT NULL CHECK (command_type IN ('pause', 'switch')),
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  acknowledged_at timestamptz,
  PRIMARY KEY (user_id, command_id)
);

CREATE INDEX IF NOT EXISTS dj_devices_last_seen_idx
  ON public.dj_devices(user_id, last_seen_at DESC);
CREATE INDEX IF NOT EXISTS dj_commands_pending_idx
  ON public.dj_device_commands(user_id, target_device_id, created_at)
  WHERE acknowledged_at IS NULL;
CREATE INDEX IF NOT EXISTS dj_library_updated_idx
  ON public.dj_library_tracks(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS dj_playlist_items_order_idx
  ON public.dj_playlist_items(user_id, playlist_id, position);

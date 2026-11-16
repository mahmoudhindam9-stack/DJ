-- DJ-Cloud cross-device synchronization schema
-- Data is accessed only through authenticated server endpoints. No client-side database credentials.

CREATE TABLE IF NOT EXISTS public.dj_user_devices (
  user_id text NOT NULL,
  device_id text NOT NULL,
  device_name text NOT NULL,
  platform text NOT NULL CHECK (platform IN ('web','android','desktop')),
  app_version text,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, device_id)
);

CREATE TABLE IF NOT EXISTS public.dj_library_items (
  user_id text NOT NULL,
  item_id text NOT NULL,
  metadata jsonb NOT NULL,
  is_favorite boolean NOT NULL DEFAULT false,
  local_only boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  PRIMARY KEY (user_id, item_id)
);

CREATE TABLE IF NOT EXISTS public.dj_playlists (
  user_id text NOT NULL,
  playlist_id text NOT NULL,
  name text NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  PRIMARY KEY (user_id, playlist_id)
);

CREATE TABLE IF NOT EXISTS public.dj_playlist_items (
  user_id text NOT NULL,
  playlist_id text NOT NULL,
  item_id text NOT NULL,
  position integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, playlist_id, item_id),
  FOREIGN KEY (user_id, playlist_id)
    REFERENCES public.dj_playlists(user_id, playlist_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.dj_player_state (
  user_id text PRIMARY KEY,
  active_device_id text,
  current_track jsonb,
  queue jsonb NOT NULL DEFAULT '[]'::jsonb,
  is_playing boolean NOT NULL DEFAULT false,
  position_ms bigint NOT NULL DEFAULT 0,
  duration_ms bigint NOT NULL DEFAULT 0,
  playback_mode text NOT NULL DEFAULT 'NORMAL'
    CHECK (playback_mode IN ('NORMAL','REPEAT_ONE','REPEAT_ALL','SHUFFLE')),
  volume real NOT NULL DEFAULT 0.85 CHECK (volume >= 0 AND volume <= 1),
  revision bigint NOT NULL DEFAULT 0,
  lease_expires_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.dj_device_commands (
  user_id text NOT NULL,
  command_id uuid NOT NULL DEFAULT gen_random_uuid(),
  target_device_id text NOT NULL,
  source_device_id text,
  command_type text NOT NULL
    CHECK (command_type IN ('pause','resume','switch','seek','refresh_state')),
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  acknowledged_at timestamptz,
  PRIMARY KEY (user_id, command_id)
);

CREATE INDEX IF NOT EXISTS dj_devices_last_seen_idx
  ON public.dj_user_devices(user_id, last_seen_at DESC);
CREATE INDEX IF NOT EXISTS dj_commands_pending_idx
  ON public.dj_device_commands(user_id, target_device_id, created_at)
  WHERE acknowledged_at IS NULL;
CREATE INDEX IF NOT EXISTS dj_library_updated_idx
  ON public.dj_library_items(user_id, updated_at DESC);

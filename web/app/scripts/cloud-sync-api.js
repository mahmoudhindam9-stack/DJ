import { Pool } from 'pg';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { createRealtime, rawSql } from '@neon/realtime/server';

const DEVICE_ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PLAYBACK_MODES = new Set(['NORMAL', 'REPEAT_ONE', 'REPEAT_ALL', 'SHUFFLE']);
const MAX_QUEUE_ITEMS = 500;
const MAX_BODY_BYTES = 1024 * 1024;

let pool;
let remoteJwks;
let realtime;

function getPool() {
  if (!process.env.DATABASE_URL) throw new Error('DJ-Cloud database is not configured.');
  if (!pool) {
    pool = new Pool({
      connectionString: process.env.DATABASE_URL,
      max: 2,
      idleTimeoutMillis: 10_000,
      connectionTimeoutMillis: 8_000,
      keepAlive: true,
    });
    pool.on('error', (error) => {
      console.error('[DJ-Cloud] PostgreSQL pool error:', error.message);
    });
  }
  return pool;
}

function getRealtime() {
  if (!process.env.NEON_REALTIME_SECRET) throw new Error('DJ-Cloud Realtime is not configured.');
  if (!realtime) realtime = createRealtime({
    secret: process.env.NEON_REALTIME_SECRET,
    db: 'neondb',
  });
  return realtime;
}

function getJwks() {
  const url = process.env.NEON_AUTH_JWKS_URL;
  if (!url) throw new Error('DJ-Cloud authentication is not configured.');
  if (!remoteJwks) remoteJwks = createRemoteJWKSet(new URL(url));
  return remoteJwks;
}

async function requireUser(req) {
  const authorization = String(req.headers?.authorization || '');
  const token = authorization.startsWith('Bearer ') ? authorization.slice(7).trim() : '';
  if (!token) return null;

  const authBase = process.env.NEON_AUTH_BASE_URL;
  if (!authBase) throw new Error('DJ-Cloud authentication is not configured.');
  const issuer = new URL(authBase).origin;

  try {
    const { payload } = await jwtVerify(token, getJwks(), {
      issuer,
      algorithms: ['EdDSA'],
    });
    return typeof payload.sub === 'string' && payload.sub.length > 0
      ? { id: payload.sub }
      : null;
  } catch {
    return null;
  }
}

async function readJsonBody(req) {
  if (req.body !== undefined) {
    if (typeof req.body === 'string') {
      if (Buffer.byteLength(req.body) > MAX_BODY_BYTES) throw new Error('Request body is too large.');
      return JSON.parse(req.body || '{}');
    }
    return req.body && typeof req.body === 'object' ? req.body : {};
  }

  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    const data = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += data.length;
    if (total > MAX_BODY_BYTES) throw new Error('Request body is too large.');
    chunks.push(data);
  }
  const raw = Buffer.concat(chunks).toString('utf8');
  return raw ? JSON.parse(raw) : {};
}

function validDeviceId(value) {
  return typeof value === 'string' && DEVICE_ID_RE.test(value);
}

function cleanTrack(value) {
  if (value == null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid track payload.');
  const track = { ...value };
  delete track.audioBlob;
  const uri = String(track.uri || '');
  if (/^(blob:|file:|content:|filesystem:)/i.test(uri)) {
    track.unavailableOnOtherDevices = true;
    track.uri = '';
  }
  return track;
}

function cleanQueue(value) {
  if (!Array.isArray(value)) return [];
  if (value.length > MAX_QUEUE_ITEMS) throw new Error('Queue is too large.');
  return value.map(cleanTrack).filter(Boolean);
}

function sendError(sendJson, res, status, message) {
  sendJson(res, status, { ok: false, error: message });
}

export async function handleCloudApi(req, res, url, sendJson) {
  const route = url.pathname.replace(/^\/api\/cloud\/?/, '');
  const method = String(req.method || 'GET').toUpperCase();

  try {
    const user = await requireUser(req);
    if (!user) {
      sendError(sendJson, res, 401, 'Please sign in to DJ Cloud.');
      return true;
    }

    const db = getPool();

    if (route === 'devices/register' && method === 'POST') {
      const body = await readJsonBody(req);
      if (!validDeviceId(body.device_id)) {
        sendError(sendJson, res, 400, 'Invalid device identifier.');
        return true;
      }
      const name = String(body.name || 'DJ Device').trim().slice(0, 80) || 'DJ Device';
      const platform = ['android', 'web', 'desktop'].includes(body.platform) ? body.platform : 'web';
      const userAgent = String(req.headers?.['user-agent'] || '').slice(0, 400);

      const registered = await db.query(
        `INSERT INTO public.dj_devices (user_id, device_id, name, platform, user_agent, last_seen_at)
         VALUES ($1, $2::uuid, $3, $4, $5, now())
         ON CONFLICT (user_id, device_id) DO UPDATE
         SET name = EXCLUDED.name, platform = EXCLUDED.platform,
             user_agent = EXCLUDED.user_agent, last_seen_at = now()
         RETURNING device_id::text, name, platform, last_seen_at`,
        [user.id, body.device_id, name, platform, userAgent]
      );

      await db.query(
        `INSERT INTO public.dj_playback_state (user_id, active_device_id)
         VALUES ($1, $2::uuid)
         ON CONFLICT (user_id) DO UPDATE
         SET active_device_id = COALESCE(public.dj_playback_state.active_device_id, EXCLUDED.active_device_id),
             revision = public.dj_playback_state.revision + 1,
             updated_at = now()`,
        [user.id, body.device_id]
      );

      sendJson(res, 200, { ok: true, data: registered.rows[0] });
      return true;
    }

    if (route === 'devices' && method === 'GET') {
      const result = await db.query(
        `SELECT device_id::text, name, platform, last_seen_at,
                (last_seen_at > now() - interval '45 seconds') AS online
         FROM public.dj_devices
         WHERE user_id = $1
         ORDER BY last_seen_at DESC
         LIMIT 30`,
        [user.id]
      );
      sendJson(res, 200, { ok: true, data: result.rows });
      return true;
    }

    if (route === 'devices/heartbeat' && method === 'POST') {
      const body = await readJsonBody(req);
      if (!validDeviceId(body.device_id)) {
        sendError(sendJson, res, 400, 'Invalid device identifier.');
        return true;
      }
      const updated = await db.query(
        `UPDATE public.dj_devices SET last_seen_at = now()
         WHERE user_id = $1 AND device_id = $2::uuid
         RETURNING device_id::text, last_seen_at`,
        [user.id, body.device_id]
      );
      if (!updated.rowCount) {
        sendError(sendJson, res, 404, 'This device is not registered.');
        return true;
      }
      sendJson(res, 200, { ok: true, data: updated.rows[0] });
      return true;
    }

    if (route === 'state' && method === 'GET') {
      const result = await db.query(
        `SELECT s.user_id, s.active_device_id::text, s.current_track, s.queue,
                s.is_playing, s.position_ms::text, s.duration_ms::text,
                s.playback_mode, s.revision::text, s.updated_at,
                d.name AS active_device_name
         FROM public.dj_playback_state s
         LEFT JOIN public.dj_devices d
           ON d.user_id = s.user_id AND d.device_id = s.active_device_id
         WHERE s.user_id = $1`,
        [user.id]
      );
      sendJson(res, 200, { ok: true, data: result.rows[0] || null });
      return true;
    }

    if (route === 'state/live' && method === 'POST') {
      const query = rawSql(
        `SELECT s.user_id, s.active_device_id::text, s.current_track, s.queue,
                s.is_playing, s.position_ms::text, s.duration_ms::text,
                s.playback_mode, s.revision::text, s.updated_at,
                d.name AS active_device_name
         FROM public.dj_playback_state s
         LEFT JOIN public.dj_devices d
           ON d.user_id = s.user_id AND d.device_id = s.active_device_id
         WHERE s.user_id = $1`,
        [user.id]
      );
      const sealedQuery = await getRealtime().seal({ query });
      sendJson(res, 200, { ok: true, data: sealedQuery });
      return true;
    }

    if (route === 'commands' && method === 'GET') {
      const deviceId = url.searchParams.get('device_id');
      if (!validDeviceId(deviceId)) {
        sendError(sendJson, res, 400, 'Invalid device identifier.');
        return true;
      }
      const pending = await db.query(
        `SELECT command_id::text, target_device_id::text, source_device_id::text,
                command_type, payload, created_at
         FROM public.dj_device_commands
         WHERE user_id = $1 AND target_device_id = $2::uuid AND acknowledged_at IS NULL
         ORDER BY created_at ASC
         LIMIT 20`,
        [user.id, deviceId]
      );
      sendJson(res, 200, { ok: true, data: pending.rows });
      return true;
    }

    if (route === 'claim' && method === 'POST') {
      const body = await readJsonBody(req);
      if (!validDeviceId(body.device_id)) {
        sendError(sendJson, res, 400, 'Invalid device identifier.');
        return true;
      }
      const client = await db.connect();
      try {
        await client.query('BEGIN');
        const registered = await client.query(
          'SELECT device_id::text FROM public.dj_devices WHERE user_id = $1 AND device_id = $2::uuid',
          [user.id, body.device_id]
        );
        if (!registered.rowCount) {
          await client.query('ROLLBACK');
          sendError(sendJson, res, 404, 'Register this device before taking control.');
          return true;
        }
        await client.query(
          'INSERT INTO public.dj_playback_state (user_id, active_device_id) VALUES ($1, $2::uuid) ON CONFLICT (user_id) DO NOTHING',
          [user.id, body.device_id]
        );
        const currentResult = await client.query(
          `SELECT active_device_id::text, is_playing, position_ms::text, current_track
           FROM public.dj_playback_state WHERE user_id = $1 FOR UPDATE`,
          [user.id]
        );
        const current = currentResult.rows[0];
        const oldDeviceId = current.active_device_id;
        if (oldDeviceId && oldDeviceId !== body.device_id && current.is_playing) {
          const online = await client.query(
            `SELECT 1 FROM public.dj_devices
             WHERE user_id = $1 AND device_id = $2::uuid
               AND last_seen_at > now() - interval '45 seconds'`,
            [user.id, oldDeviceId]
          );
          if (online.rowCount) {
            const alreadyPending = await client.query(
              `SELECT command_id::text FROM public.dj_device_commands
               WHERE user_id = $1 AND target_device_id = $2::uuid
                 AND command_type = 'pause' AND acknowledged_at IS NULL
               ORDER BY created_at DESC LIMIT 1`,
              [user.id, oldDeviceId]
            );
            let commandId = alreadyPending.rows[0]?.command_id;
            if (!commandId) {
              const command = await client.query(
                `INSERT INTO public.dj_device_commands
                   (user_id, target_device_id, source_device_id, command_type, payload)
                 VALUES ($1, $2::uuid, $3::uuid, 'pause',
                         jsonb_build_object('next_device_id', $3::text))
                 RETURNING command_id::text`,
                [user.id, oldDeviceId, body.device_id]
              );
              commandId = command.rows[0].command_id;
            }
            await client.query('COMMIT');
            sendJson(res, 200, { ok: true, data: { status: 'handoff_pending', command_id: commandId, active_device_id: oldDeviceId } });
            return true;
          }
        }

        const claimed = await client.query(
          `UPDATE public.dj_playback_state
           SET active_device_id = $2::uuid, is_playing = false,
               revision = revision + 1, updated_at = now()
           WHERE user_id = $1
           RETURNING active_device_id::text, current_track, queue, position_ms::text,
                     duration_ms::text, playback_mode, is_playing, revision::text, updated_at`,
          [user.id, body.device_id]
        );
        await client.query('COMMIT');
        sendJson(res, 200, { ok: true, data: { status: 'active', player: claimed.rows[0] } });
        return true;
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
      } finally {
        client.release();
      }
    }

    if (route === 'commands/ack' && method === 'POST') {
      const body = await readJsonBody(req);
      if (!validDeviceId(body.device_id) || typeof body.command_id !== 'string') {
        sendError(sendJson, res, 400, 'Invalid command acknowledgement.');
        return true;
      }
      const client = await db.connect();
      try {
        await client.query('BEGIN');
        const command = await client.query(
          `SELECT command_id::text, command_type, payload
           FROM public.dj_device_commands
           WHERE user_id = $1 AND command_id = $2::uuid
             AND target_device_id = $3::uuid AND acknowledged_at IS NULL
           FOR UPDATE`,
          [user.id, body.command_id, body.device_id]
        );
        if (!command.rowCount) {
          await client.query('ROLLBACK');
          sendError(sendJson, res, 404, 'This command is no longer pending.');
          return true;
        }
        const state = await client.query(
          `SELECT active_device_id::text FROM public.dj_playback_state
           WHERE user_id = $1 FOR UPDATE`,
          [user.id]
        );
        if (state.rows[0]?.active_device_id !== body.device_id) {
          await client.query('ROLLBACK');
          sendError(sendJson, res, 409, 'Only the active playback device can acknowledge this handoff.');
          return true;
        }

        const record = command.rows[0];
        if (record.command_type === 'pause') {
          const nextDeviceId = record.payload?.next_device_id;
          if (!validDeviceId(nextDeviceId)) {
            await client.query('ROLLBACK');
            sendError(sendJson, res, 400, 'The target device for this handoff is invalid.');
            return true;
          }
          await client.query(
            `UPDATE public.dj_playback_state
             SET active_device_id = $2::uuid, is_playing = false,
                 position_ms = $3, revision = revision + 1, updated_at = now()
             WHERE user_id = $1 AND active_device_id = $4::uuid`,
            [
              user.id,
              nextDeviceId,
              Math.max(0, Math.min(86_400_000, Math.floor(Number(body.position_ms) || 0))),
              body.device_id,
            ]
          );
        }
        await client.query(
          'UPDATE public.dj_device_commands SET acknowledged_at = now() WHERE user_id = $1 AND command_id = $2::uuid',
          [user.id, body.command_id]
        );
        await client.query('COMMIT');
        sendJson(res, 200, { ok: true, data: { acknowledged: true } });
        return true;
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
      } finally {
        client.release();
      }
    }

    if (route === 'state' && method === 'PUT') {
      const body = await readJsonBody(req);
      if (!validDeviceId(body.device_id)) {
        sendError(sendJson, res, 400, 'Invalid device identifier.');
        return true;
      }
      const currentTrack = cleanTrack(body.current_track);
      const queue = cleanQueue(body.queue);
      const positionMs = Math.max(0, Math.min(86_400_000, Math.floor(Number(body.position_ms) || 0)));
      const durationMs = Math.max(0, Math.min(86_400_000, Math.floor(Number(body.duration_ms) || 0)));
      const playbackMode = PLAYBACK_MODES.has(body.playback_mode) ? body.playback_mode : 'NORMAL';
      const isPlaying = Boolean(body.is_playing);

      const result = await db.query(
        `UPDATE public.dj_playback_state s
         SET current_track = $3::jsonb,
             queue = $4::jsonb,
             is_playing = $5,
             position_ms = $6,
             duration_ms = $7,
             playback_mode = $8,
             revision = s.revision + 1,
             updated_at = now()
         WHERE s.user_id = $1 AND s.active_device_id = $2::uuid
         RETURNING s.revision::text, s.updated_at, pg_current_xact_id()::text AS txid`,
        [
          user.id,
          body.device_id,
          currentTrack == null ? null : JSON.stringify(currentTrack),
          JSON.stringify(queue),
          isPlaying,
          positionMs,
          durationMs,
          playbackMode,
        ]
      );
      if (!result.rowCount) {
        sendError(sendJson, res, 409, 'This device does not own playback control.');
        return true;
      }
      sendJson(res, 200, { ok: true, data: result.rows[0] });
      return true;
    }

    if (route === 'library' && method === 'GET') {
      const result = await db.query(
        `SELECT track_id, track, is_favorite, added_at, updated_at
         FROM public.dj_library_tracks
         WHERE user_id = $1
         ORDER BY updated_at DESC
         LIMIT 5000`,
        [user.id]
      );
      sendJson(res, 200, { ok: true, data: result.rows });
      return true;
    }

    if (route === 'library' && method === 'PUT') {
      const body = await readJsonBody(req);
      if (!Array.isArray(body.tracks) || body.tracks.length > 500) {
        sendError(sendJson, res, 400, 'Send between 1 and 500 tracks per batch.');
        return true;
      }
      const client = await db.connect();
      try {
        await client.query('BEGIN');
        for (const entry of body.tracks) {
          const track = cleanTrack(entry?.track);
          const trackId = String(entry?.track_id || track?.id || '').trim().slice(0, 300);
          if (!track || !trackId) continue;
          await client.query(
            `INSERT INTO public.dj_library_tracks (user_id, track_id, track, is_favorite, added_at, updated_at)
             VALUES ($1, $2, $3::jsonb, $4, COALESCE($5::timestamptz, now()), now())
             ON CONFLICT (user_id, track_id) DO UPDATE
             SET track = EXCLUDED.track,
                 is_favorite = EXCLUDED.is_favorite,
                 updated_at = now()`,
            [
              user.id,
              trackId,
              JSON.stringify(track),
              Boolean(entry.is_favorite ?? track.isFavorite),
              entry.added_at || null,
            ]
          );
        }
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK');
        throw error;
      } finally {
        client.release();
      }
      sendJson(res, 200, { ok: true, data: { saved: body.tracks.length } });
      return true;
    }

    if (route.startsWith('library/') && method === 'DELETE') {
      const trackId = decodeURIComponent(route.slice('library/'.length));
      const result = await db.query(
        'DELETE FROM public.dj_library_tracks WHERE user_id = $1 AND track_id = $2 RETURNING track_id',
        [user.id, trackId]
      );
      sendJson(res, 200, { ok: true, data: { deleted: result.rowCount > 0 } });
      return true;
    }

    if (route === 'library/favorite' && method === 'PATCH') {
      const body = await readJsonBody(req);
      const trackId = String(body.track_id || '').trim().slice(0, 300);
      if (!trackId || typeof body.is_favorite !== 'boolean') {
        sendError(sendJson, res, 400, 'A track_id and boolean is_favorite are required.');
        return true;
      }
      const result = await db.query(
        `UPDATE public.dj_library_tracks
         SET is_favorite = $3, updated_at = now()
         WHERE user_id = $1 AND track_id = $2
         RETURNING track_id, is_favorite`,
        [user.id, trackId, body.is_favorite]
      );
      if (!result.rowCount) {
        sendError(sendJson, res, 404, 'Track not found in your DJ Cloud library.');
        return true;
      }
      sendJson(res, 200, { ok: true, data: result.rows[0] });
      return true;
    }

    if (route === 'playlists' && method === 'GET') {
      const result = await db.query(
        `SELECT p.playlist_id, p.name, p.created_at, p.updated_at,
                COALESCE(jsonb_agg(
                  jsonb_build_object(
                    'track_id', i.track_id,
                    'position', i.position,
                    'track', l.track,
                    'is_favorite', l.is_favorite
                  ) ORDER BY i.position, i.added_at
                ) FILTER (WHERE i.track_id IS NOT NULL), '[]'::jsonb) AS items
         FROM public.dj_playlists p
         LEFT JOIN public.dj_playlist_items i
           ON i.user_id = p.user_id AND i.playlist_id = p.playlist_id
         LEFT JOIN public.dj_library_tracks l
           ON l.user_id = i.user_id AND l.track_id = i.track_id
         WHERE p.user_id = $1
         GROUP BY p.user_id, p.playlist_id
         ORDER BY p.updated_at DESC`,
        [user.id]
      );
      sendJson(res, 200, { ok: true, data: result.rows });
      return true;
    }

    if (route === 'playlists' && method === 'POST') {
      const body = await readJsonBody(req);
      const name = String(body.name || '').trim().slice(0, 120);
      const playlistId = String(body.playlist_id || `pl_${crypto.randomUUID()}`).slice(0, 160);
      if (!name) {
        sendError(sendJson, res, 400, 'Playlist name is required.');
        return true;
      }
      const result = await db.query(
        `INSERT INTO public.dj_playlists (user_id, playlist_id, name, updated_at)
         VALUES ($1, $2, $3, now())
         ON CONFLICT (user_id, playlist_id) DO UPDATE
         SET name = EXCLUDED.name, updated_at = now()
         RETURNING playlist_id, name, created_at, updated_at`,
        [user.id, playlistId, name]
      );
      sendJson(res, 200, { ok: true, data: result.rows[0] });
      return true;
    }

    if (route.startsWith('playlists/') && !route.slice('playlists/'.length).includes('/') && method === 'DELETE') {
      const playlistId = decodeURIComponent(route.slice('playlists/'.length));
      if (playlistId.includes('/')) {
        sendError(sendJson, res, 400, 'Invalid playlist identifier.');
        return true;
      }
      const result = await db.query(
        'DELETE FROM public.dj_playlists WHERE user_id = $1 AND playlist_id = $2 RETURNING playlist_id',
        [user.id, playlistId]
      );
      sendJson(res, 200, { ok: true, data: { deleted: result.rowCount > 0 } });
      return true;
    }

    if (route.startsWith('playlists/') && route.endsWith('/items') && method === 'POST') {
      const body = await readJsonBody(req);
      const playlistId = decodeURIComponent(route.slice('playlists/'.length, -'/items'.length));
      const trackId = String(body.track_id || '').trim().slice(0, 300);
      const position = Math.max(0, Math.min(100_000, Math.floor(Number(body.position) || 0)));
      if (!trackId || !playlistId) {
        sendError(sendJson, res, 400, 'Playlist and track identifiers are required.');
        return true;
      }
      const result = await db.query(
        `INSERT INTO public.dj_playlist_items (user_id, playlist_id, track_id, position)
         SELECT $1, p.playlist_id, $3, $4
         FROM public.dj_playlists p
         WHERE p.user_id = $1 AND p.playlist_id = $2
         ON CONFLICT (user_id, playlist_id, track_id) DO UPDATE
         SET position = EXCLUDED.position
         RETURNING playlist_id, track_id, position`,
        [user.id, playlistId, trackId, position]
      );
      if (!result.rowCount) {
        sendError(sendJson, res, 404, 'Playlist not found in your DJ Cloud account.');
        return true;
      }
      await db.query(
        'UPDATE public.dj_playlists SET updated_at = now() WHERE user_id = $1 AND playlist_id = $2',
        [user.id, playlistId]
      );
      sendJson(res, 200, { ok: true, data: result.rows[0] });
      return true;
    }

    if (route.startsWith('playlists/') && route.includes('/items/') && method === 'DELETE') {
      const [, rest] = route.split('playlists/');
      const [playlistPart, trackPart] = rest.split('/items/');
      const playlistId = decodeURIComponent(playlistPart || '');
      const trackId = decodeURIComponent(trackPart || '');
      const result = await db.query(
        'DELETE FROM public.dj_playlist_items WHERE user_id = $1 AND playlist_id = $2 AND track_id = $3 RETURNING track_id',
        [user.id, playlistId, trackId]
      );
      sendJson(res, 200, { ok: true, data: { deleted: result.rowCount > 0 } });
      return true;
    }

    sendError(sendJson, res, 404, 'DJ Cloud endpoint not found.');
    return true;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'DJ Cloud request failed.';
    const status = /too large|invalid .*payload|invalid device/i.test(message) ? 400 : 500;
    console.error('[DJ-Cloud] API error:', message);
    sendError(sendJson, res, status, status === 400 ? message : 'DJ Cloud is temporarily unavailable.');
    return true;
  }
}

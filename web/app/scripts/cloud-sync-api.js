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
    db: 'dj_cloud',
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
      audience: issuer,
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

    if (route === 'claim' && method === 'POST') {
      const body = await readJsonBody(req);
      if (!validDeviceId(body.device_id)) {
        sendError(sendJson, res, 400, 'Invalid device identifier.');
        return true;
      }
      const result = await db.query(
        `UPDATE public.dj_playback_state s
         SET active_device_id = $2::uuid,
             revision = s.revision + 1,
             updated_at = now()
         WHERE s.user_id = $1
           AND EXISTS (
             SELECT 1 FROM public.dj_devices d
             WHERE d.user_id = $1 AND d.device_id = $2::uuid
           )
         RETURNING s.user_id, s.active_device_id::text, s.current_track, s.queue,
                   s.is_playing, s.position_ms::text, s.duration_ms::text,
                   s.playback_mode, s.revision::text, s.updated_at`,
        [user.id, body.device_id]
      );
      if (!result.rowCount) {
        sendError(sendJson, res, 404, 'Register this device before taking control.');
        return true;
      }
      const state = result.rows[0];
      sendJson(res, 200, { ok: true, data: state });
      return true;
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

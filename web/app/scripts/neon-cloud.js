import crypto from 'node:crypto';

/**
 * Neon Cloud configuration for DJ-Cloud (dark-fire-99620103, branch main / br-spring-term-baakf0qx).
 * Reads existing Vercel / environment variables without ever logging or exposing secrets.
 */
export const NEON_CLOUD_CONFIG = {
  projectName: 'DJ-Cloud',
  projectId: 'dark-fire-99620103',
  organizationId: 'org-jolly-sun-87195223',
  branchName: 'main',
  branchId: 'br-spring-term-baakf0qx',
  region: 'aws-eu-central-1',
  database: 'neondb',
  get authBaseUrl() {
    return (
      process.env.NEON_AUTH_BASE_URL ||
      process.env.VITE_NEON_AUTH_URL ||
      'https://ep-morning-bird-bad5xn0x.neonauth.c-8.eu-central-1.aws.neon.tech/neondb/auth'
    );
  },
  get jwksUrl() {
    return (
      process.env.NEON_AUTH_JWKS_URL ||
      'https://ep-morning-bird-bad5xn0x.neonauth.c-8.eu-central-1.aws.neon.tech/neondb/auth/.well-known/jwks.json'
    );
  },
  get realtimeUrl() {
    return (
      process.env.NEON_REALTIME_URL ||
      process.env.VITE_NEON_REALTIME_URL ||
      'https://ep-morning-bird-bad5xn0x.rt.c-8.eu-central-1.aws.neon.tech/'
    );
  },
  get hasDatabaseUrl() {
    return Boolean(process.env.DATABASE_URL && process.env.DATABASE_URL.trim());
  }
};

/**
 * Canonical, unified SQL DDL resolving all schema mismatches between Android & Web
 * and enforcing composite primary keys (user_id, id) for strict per-user isolation.
 */
export const NEON_CANONICAL_SCHEMA_SQL = `
CREATE TABLE IF NOT EXISTS users (
  user_id TEXT PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  display_name TEXT NOT NULL DEFAULT '',
  avatar_url TEXT DEFAULT '',
  auth_provider TEXT NOT NULL DEFAULT 'email',
  password_hash TEXT DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_tracks (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  title TEXT NOT NULL DEFAULT '',
  artist TEXT NOT NULL DEFAULT '',
  album TEXT NOT NULL DEFAULT '',
  duration_ms BIGINT NOT NULL DEFAULT 0,
  uri TEXT NOT NULL DEFAULT '',
  source_type TEXT NOT NULL DEFAULT 'LOCAL',
  is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
  added_at BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, id)
);

CREATE INDEX IF NOT EXISTS idx_user_tracks_user_id ON user_tracks(user_id);

CREATE TABLE IF NOT EXISTS user_playlists (
  id TEXT NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  name TEXT NOT NULL DEFAULT '',
  song_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, id)
);

CREATE INDEX IF NOT EXISTS idx_user_playlists_user_id ON user_playlists(user_id);

CREATE TABLE IF NOT EXISTS user_settings (
  user_id TEXT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
  theme TEXT NOT NULL DEFAULT 'DARK',
  language_mode TEXT NOT NULL DEFAULT 'auto',
  eq_state_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  custom_presets_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_playback_state (
  user_id TEXT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
  active_device_id TEXT NOT NULL DEFAULT '',
  active_device_platform TEXT NOT NULL DEFAULT 'web',
  is_playing BOOLEAN NOT NULL DEFAULT FALSE,
  current_track_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  queue_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  position_ms BIGINT NOT NULL DEFAULT 0,
  playback_mode TEXT NOT NULL DEFAULT 'NORMAL',
  volume DOUBLE PRECISION NOT NULL DEFAULT 0.85,
  updated_at BIGINT NOT NULL DEFAULT 0
);
`;

function sha256(value) {
  return crypto.createHash('sha256').update(String(value), 'utf8').digest('hex');
}

function getSigningSecret() {
  const secret = process.env.NEON_REALTIME_SECRET;
  if (secret && secret.length >= 32) return secret;
  if (neonCloudStore?.testMode) return 'dj-cloud-test-only-session-secret-32-characters-minimum';
  throw new Error('NEON_REALTIME_SECRET must be configured with at least 32 characters.');
}

export function createSignedSessionToken(payload) {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify({
    sub: payload.userId,
    userId: payload.userId,
    email: payload.email,
    displayName: payload.displayName || '',
    authProvider: payload.authProvider || 'email',
    jti: crypto.randomUUID(),
    iat: now,
    exp: now + 60 * 60 * 24 * 7
  })).toString('base64url');
  const signingInput = header + '.' + body;
  const signature = crypto.createHmac('sha256', getSigningSecret()).update(signingInput).digest('base64url');
  return signingInput + '.' + signature;
}

export function verifySessionToken(token) {
  if (!token || typeof token !== 'string') return null;
  const cleaned = token.replace(/^Bearer\s+/i, '').trim();
  const parts = cleaned.split('.');
  if (parts.length !== 3) return null;
  try {
    const header = JSON.parse(Buffer.from(parts[0], 'base64url').toString('utf8'));
    if (header?.alg !== 'HS256' || header?.typ !== 'JWT') return null;
    const expected = crypto.createHmac('sha256', getSigningSecret())
      .update(parts[0] + '.' + parts[1])
      .digest();
    const received = Buffer.from(parts[2], 'base64url');
    if (received.length !== expected.length || !crypto.timingSafeEqual(received, expected)) return null;
    const decoded = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    const now = Math.floor(Date.now() / 1000);
    if (!decoded?.userId || decoded.sub !== decoded.userId ||
        !Number.isFinite(decoded.exp) || !Number.isFinite(decoded.iat) ||
        decoded.exp <= now || decoded.iat > now + 60) return null;
    return decoded;
  } catch {
    return null;
  }
}

/** A failed Neon request is an API failure, never a successful in-memory fallback. */
async function executeNeonHttpSql(query, params = []) {
  const dbUrl = process.env.DATABASE_URL;
  if (!dbUrl) throw new Error('Neon DATABASE_URL is not configured.');
  let parsed;
  try {
    parsed = new URL(dbUrl);
  } catch {
    throw new Error('Neon DATABASE_URL is invalid.');
  }
  if (parsed.protocol !== 'postgres:' && parsed.protocol !== 'postgresql:') {
    throw new Error('Neon DATABASE_URL must be a PostgreSQL connection string.');
  }
  let response;
  try {
    response = await fetch('https://' + parsed.hostname + '/sql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Neon-Connection-String': dbUrl
      },
      body: JSON.stringify({ query, params })
    });
  } catch {
    throw new Error('Could not reach the Neon SQL endpoint.');
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const message = typeof payload?.message === 'string' ? payload.message : '';
    throw new Error(message || ('Neon SQL request failed (HTTP ' + response.status + ').'));
  }
  if (!payload || typeof payload !== 'object') {
    throw new Error('Neon SQL endpoint returned an invalid response.');
  }
  return payload;
}

function resultRows(result) {
  if (Array.isArray(result?.rows)) return result.rows;
  if (Array.isArray(result?.results?.[0]?.rows)) return result.results[0].rows;
  return [];
}

function deriveScrypt(password, salt, length = 64) {
  return new Promise((resolve, reject) => {
    crypto.scrypt(String(password), salt, length,
      { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 },
      (error, derived) => error ? reject(error) : resolve(derived));
  });
}

async function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const derived = await deriveScrypt(password, salt, 64);
  return 'scrypt$' + salt.toString('base64url') + '$' + derived.toString('base64url');
}

async function verifyPassword(password, email, storedHash) {
  if (!storedHash) return false;
  // Legacy SHA-256 is retained only for verification and upgraded after a successful login.
  if (/^[a-f0-9]{64}$/i.test(storedHash)) {
    const actual = Buffer.from(sha256(email + ':' + password), 'hex');
    const expected = Buffer.from(storedHash, 'hex');
    return actual.length === expected.length && crypto.timingSafeEqual(actual, expected);
  }
  const match = /^scrypt\$([A-Za-z0-9_-]+)\$([A-Za-z0-9_-]+)$/.exec(storedHash);
  if (!match) return false;
  try {
    const salt = Buffer.from(match[1], 'base64url');
    const expected = Buffer.from(match[2], 'base64url');
    const actual = await deriveScrypt(password, salt, expected.length);
    return actual.length === expected.length && crypto.timingSafeEqual(actual, expected);
  } catch {
    return false;
  }
}

let schemaInitialized = false;
let schemaPromise = null;
export async function ensureNeonSchema() {
  if (neonCloudStore?.testMode) return;
  if (schemaInitialized) return;
  if (!NEON_CLOUD_CONFIG.hasDatabaseUrl) throw new Error('Neon DATABASE_URL is not configured.');
  if (!schemaPromise) {
    schemaPromise = (async () => {
      const statements = NEON_CANONICAL_SCHEMA_SQL.split(';').map((value) => value.trim()).filter(Boolean);
      for (const statement of statements) await executeNeonHttpSql(statement);
      schemaInitialized = true;
    })().catch((error) => {
      schemaPromise = null;
      throw error;
    });
  }
  await schemaPromise;
}

export async function checkNeonConnection() {
  await ensureNeonSchema();
  const rows = resultRows(await executeNeonHttpSql('SELECT current_database() AS database_name, NOW() AS checked_at'));
  if (!rows.length) throw new Error('Neon returned no health-check row.');
  return { database: rows[0].database_name, checkedAt: rows[0].checked_at };
}

/**
 * Isolated per-user store (mirroring the canonical Neon tables and syncing with Neon SQL when configured).
 */
class NeonCloudStore {
  constructor() {
    /** @type {Map<string, {userId: string, email: string, displayName: string, authProvider: string, passwordHash: string, createdAt: number}>} */
    this.usersByEmail = new Map();
    /** @type {Map<string, {userId: string, email: string, displayName: string, authProvider: string, passwordHash: string, createdAt: number}>} */
    this.usersById = new Map();
    /** @type {Map<string, {tracks: any[], playlists: any[], favoriteIds: string[], settings: any, playbackState: any, updatedAt: number}>} */
    this.vaultByUserId = new Map();
    this.testMode = false;
  }

  resetForTests() {
    this.testMode = true;
    this.usersByEmail.clear();
    this.usersById.clear();
    this.vaultByUserId.clear();
  }

  ensureUserVault(userId) {
    if (!this.vaultByUserId.has(userId)) {
      this.vaultByUserId.set(userId, {
        tracks: [],
        playlists: [],
        favoriteIds: [],
        settings: {
          theme: 'DARK',
          languageMode: 'auto',
          eqState: { enabled: true, presetName: 'Flat' },
          customPresets: []
        },
        playbackState: {
          userId,
          activeDeviceId: '',
          activeDevicePlatform: 'web',
          isPlaying: false,
          currentTrackId: '',
          currentTrackTitle: '',
          currentTrackArtist: '',
          currentTrackUri: '',
          positionMs: 0,
          playbackMode: 'NORMAL',
          queueIds: [],
          updatedAt: 0
        },
        updatedAt: Date.now()
      });
    }
    return this.vaultByUserId.get(userId);
  }

  async signUpEmail({ email, password, displayName }) {
    const normalizedEmail = String(email || '').trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
      const err = new Error('Invalid email address');
      err.statusCode = 400;
      throw err;
    }
    if (!password || String(password).length < 6) {
      const err = new Error('Password must be at least 6 characters');
      err.statusCode = 400;
      throw err;
    }
    const resolvedName = String(displayName || '').trim() || normalizedEmail.split('@')[0];
    const userId = 'usr_' + sha256(normalizedEmail).slice(0, 20);

    if (this.testMode) {
      const existing = this.usersByEmail.get(normalizedEmail);
      if (existing) {
        const err = new Error('An account with this email already exists. Sign in instead.');
        err.statusCode = 409;
        throw err;
      }
      const userRecord = {
        userId, email: normalizedEmail, displayName: resolvedName,
        authProvider: 'email',
        passwordHash: sha256(normalizedEmail + ':' + password),
        createdAt: Date.now()
      };
      this.usersByEmail.set(normalizedEmail, userRecord);
      this.usersById.set(userId, userRecord);
      this.ensureUserVault(userId);
      return {
        userId, email: normalizedEmail, displayName: resolvedName,
        authProvider: 'email', sessionToken: createSignedSessionToken(userRecord)
      };
    }

    await ensureNeonSchema();
    const existing = resultRows(await executeNeonHttpSql(
      'SELECT user_id FROM public.users WHERE email = $1 LIMIT 1',
      [normalizedEmail]
    ))[0];
    if (existing) {
      const err = new Error('An account with this email already exists. Sign in instead.');
      err.statusCode = 409;
      throw err;
    }
    const passwordHash = await hashPassword(password);
    const inserted = resultRows(await executeNeonHttpSql(
      'INSERT INTO public.users (user_id, email, display_name, auth_provider, password_hash) ' +
      "VALUES ($1, $2, $3, 'email', $4) ON CONFLICT (email) DO NOTHING RETURNING user_id",
      [userId, normalizedEmail, resolvedName, passwordHash]
    ));
    if (!inserted.length) {
      const err = new Error('An account with this email already exists. Sign in instead.');
      err.statusCode = 409;
      throw err;
    }
    const userRecord = { userId, email: normalizedEmail, displayName: resolvedName, authProvider: 'email' };
    return { ...userRecord, sessionToken: createSignedSessionToken(userRecord) };
  }

  async signInEmail({ email, password }) {
    const normalizedEmail = String(email || '').trim().toLowerCase();
    if (!normalizedEmail || !password) {
      const err = new Error('Email and password are required');
      err.statusCode = 400;
      throw err;
    }

    if (this.testMode) {
      const existing = this.usersByEmail.get(normalizedEmail);
      const actual = sha256(normalizedEmail + ':' + password);
      if (!existing || !existing.passwordHash || existing.passwordHash !== actual) {
        const err = new Error('Invalid email or password');
        err.statusCode = 401;
        throw err;
      }
      return {
        userId: existing.userId, email: existing.email, displayName: existing.displayName,
        authProvider: existing.authProvider || 'email',
        sessionToken: createSignedSessionToken(existing)
      };
    }

    await ensureNeonSchema();
    const existing = resultRows(await executeNeonHttpSql(
      'SELECT user_id, email, display_name, auth_provider, password_hash ' +
      'FROM public.users WHERE email = $1 LIMIT 1',
      [normalizedEmail]
    ))[0];
    if (!existing || !await verifyPassword(password, normalizedEmail, existing.password_hash)) {
      const err = new Error('Invalid email or password');
      err.statusCode = 401;
      throw err;
    }
    if (/^[a-f0-9]{64}$/i.test(String(existing.password_hash || ''))) {
      const upgradedHash = await hashPassword(password);
      await executeNeonHttpSql(
        'UPDATE public.users SET password_hash = $2, updated_at = NOW() WHERE user_id = $1',
        [existing.user_id, upgradedHash]
      );
    }
    const userRecord = {
      userId: existing.user_id,
      email: existing.email,
      displayName: existing.display_name || normalizedEmail.split('@')[0],
      authProvider: existing.auth_provider || 'email'
    };
    return { ...userRecord, authProvider: 'email', sessionToken: createSignedSessionToken(userRecord) };
  }

  async signInGoogle({ idToken }) {
    if (this.testMode) {
      const email = 'google_' + Date.now() + '@gmail.com';
      const userId = 'usr_' + sha256(email).slice(0, 20);
      const userRecord = { userId, email, displayName: 'Google DJ User', authProvider: 'google' };
      this.usersByEmail.set(email, userRecord);
      this.usersById.set(userId, userRecord);
      this.ensureUserVault(userId);
      return { ...userRecord, sessionToken: createSignedSessionToken(userRecord) };
    }

    const allowedAudiences = String(process.env.GOOGLE_OAUTH_CLIENT_IDS || '')
      .split(',').map((value) => value.trim()).filter(Boolean);
    if (!allowedAudiences.length) {
      const err = new Error('Google sign-in is not configured on the DJ backend yet. Use email and password for now.');
      err.statusCode = 503;
      throw err;
    }
    if (!idToken || typeof idToken !== 'string') {
      const err = new Error('A verified Google ID token is required.');
      err.statusCode = 401;
      throw err;
    }

    const tokenUrl = new URL('https://oauth2.googleapis.com/tokeninfo');
    tokenUrl.searchParams.set('id_token', idToken);
    const tokenResponse = await fetch(tokenUrl);
    const tokenInfo = await tokenResponse.json().catch(() => null);
    if (!tokenResponse.ok || !tokenInfo?.email || tokenInfo.email_verified !== 'true' ||
        !allowedAudiences.includes(String(tokenInfo.aud || ''))) {
      const err = new Error('Google could not verify this sign-in. Please try again.');
      err.statusCode = 401;
      throw err;
    }

    const email = String(tokenInfo.email).trim().toLowerCase();
    await ensureNeonSchema();
    const existing = resultRows(await executeNeonHttpSql(
      'SELECT user_id, display_name FROM public.users WHERE email = $1 LIMIT 1',
      [email]
    ))[0];
    const userId = existing?.user_id || ('usr_' + sha256(email).slice(0, 20));
    const displayName = existing?.display_name || String(tokenInfo.name || email.split('@')[0]);
    await executeNeonHttpSql(
      'INSERT INTO public.users (user_id, email, display_name, auth_provider, password_hash) ' +
      "VALUES ($1, $2, $3, 'google', '') ON CONFLICT (email) DO UPDATE SET " +
      "display_name = COALESCE(NULLIF(public.users.display_name, ''), EXCLUDED.display_name), " +
      "auth_provider = CASE WHEN public.users.auth_provider = 'email' THEN public.users.auth_provider ELSE 'google' END, " +
      'updated_at = NOW()',
      [userId, email, displayName]
    );
    const userRecord = { userId, email, displayName, authProvider: 'google' };
    return { ...userRecord, sessionToken: createSignedSessionToken(userRecord) };
  }

  /**
   * Resolves the authenticated userId from Authorization token or verified handoff header.
   * Prevents cross-account access if a caller attempts to read/write another user's userId.
   */
  resolveAuthorizedUserId(req, requestedUserId) {
    const authHeader = req.headers?.authorization || req.headers?.Authorization || '';
    const verified = verifySessionToken(authHeader);
    if (!verified?.userId) {
      const err = new Error('Unauthorized: a valid signed session is required.');
      err.statusCode = 401;
      throw err;
    }
    if (requestedUserId && String(requestedUserId) !== verified.userId) {
      const err = new Error('Forbidden: cross-user data access denied');
      err.statusCode = 403;
      throw err;
    }
    return verified.userId;
  }

  async getUserState(userId) {
    if (this.testMode) {
      const vault = this.ensureUserVault(userId);
      return {
        userId, tracks: vault.tracks, playlists: vault.playlists,
        favoriteIds: vault.favoriteIds, settings: vault.settings,
        playbackState: vault.playbackState, updatedAt: vault.updatedAt
      };
    }

    await ensureNeonSchema();
    const results = await Promise.all([
      executeNeonHttpSql(
        'SELECT id, title, artist, album, duration_ms, uri, source_type, is_favorite, added_at, updated_at ' +
        'FROM public.user_tracks WHERE user_id = $1 ORDER BY added_at DESC', [userId]),
      executeNeonHttpSql(
        'SELECT id, name, song_ids_json, created_at, updated_at ' +
        'FROM public.user_playlists WHERE user_id = $1 ORDER BY created_at DESC', [userId]),
      executeNeonHttpSql(
        'SELECT theme, language_mode, eq_state_json, custom_presets_json, updated_at ' +
        'FROM public.user_settings WHERE user_id = $1 LIMIT 1', [userId]),
      executeNeonHttpSql(
        'SELECT active_device_id, active_device_platform, is_playing, current_track_json, ' +
        'queue_json, position_ms, playback_mode, volume, updated_at ' +
        'FROM public.user_playback_state WHERE user_id = $1 LIMIT 1', [userId])
    ]);
    const trackRows = resultRows(results[0]);
    const playlistRows = resultRows(results[1]);
    const settingsRow = resultRows(results[2])[0] || null;
    const playbackRow = resultRows(results[3])[0] || null;

    const tracks = trackRows.map((row) => ({
      id: String(row.id),
      title: String(row.title || ''),
      artist: String(row.artist || ''),
      album: String(row.album || ''),
      duration: Number(row.duration_ms || 0),
      durationMs: Number(row.duration_ms || 0),
      uri: String(row.uri || ''),
      sourceType: String(row.source_type || 'LOCAL'),
      isFavorite: Boolean(row.is_favorite),
      addedDate: Number(row.added_at || 0),
      updatedAt: Number(row.updated_at || 0)
    }));
    const playlists = playlistRows.map((row) => {
      const ids = Array.isArray(row.song_ids_json) ? row.song_ids_json : [];
      return {
        id: String(row.id), name: String(row.name || ''), songIds: ids,
        createdAt: Number(row.created_at || 0), updatedAt: Number(row.updated_at || 0),
        songCount: ids.length
      };
    });
    const settings = {
      theme: settingsRow?.theme || 'DARK',
      themeName: settingsRow?.theme || 'DARK',
      languageMode: settingsRow?.language_mode || 'auto',
      eqState: settingsRow?.eq_state_json || {},
      customPresets: settingsRow?.custom_presets_json || []
    };
    const currentTrack = playbackRow?.current_track_json || {};
    const playbackState = playbackRow ? {
      userId,
      activeDeviceId: String(playbackRow.active_device_id || ''),
      activeDevicePlatform: String(playbackRow.active_device_platform || 'web'),
      isPlaying: Boolean(playbackRow.is_playing),
      currentTrackId: String(currentTrack.id || ''),
      currentTrackTitle: String(currentTrack.title || ''),
      currentTrackArtist: String(currentTrack.artist || ''),
      currentTrackUri: String(currentTrack.uri || ''),
      positionMs: Number(playbackRow.position_ms || 0),
      playbackMode: String(playbackRow.playback_mode || 'NORMAL'),
      queueIds: Array.isArray(playbackRow.queue_json) ? playbackRow.queue_json : [],
      updatedAt: Number(playbackRow.updated_at || 0),
      volume: Number(playbackRow.volume ?? 0.85)
    } : {
      userId, activeDeviceId: '', activeDevicePlatform: 'web', isPlaying: false,
      currentTrackId: '', currentTrackTitle: '', currentTrackArtist: '', currentTrackUri: '',
      positionMs: 0, playbackMode: 'NORMAL', queueIds: [], updatedAt: 0, volume: 0.85
    };
    const stamps = [
      Number(settingsRow?.updated_at || 0), Number(playbackRow?.updated_at || 0),
      ...trackRows.map((row) => Number(row.updated_at || 0)),
      ...playlistRows.map((row) => Number(row.updated_at || 0))
    ];
    return {
      userId, tracks, playlists,
      favoriteIds: tracks.filter((track) => track.isFavorite).map((track) => track.id),
      settings, settingsPersisted: Boolean(settingsRow), playbackState, updatedAt: Math.max(0, ...stamps)
    };
  }

  async updateUserState(userId, payload) {
    if (this.testMode) {
      const vault = this.ensureUserVault(userId);
      if (Array.isArray(payload.tracks)) vault.tracks = payload.tracks.map((track) => ({ ...track, userId }));
      if (Array.isArray(payload.playlists)) vault.playlists = payload.playlists.map((playlist) => ({ ...playlist, userId }));
      if (Array.isArray(payload.favoriteIds)) vault.favoriteIds = Array.from(new Set(payload.favoriteIds.map(String)));
      if (payload.languageMode) vault.settings.languageMode = payload.languageMode;
      if (payload.themeName) vault.settings.theme = payload.themeName;
      if (payload.settings && typeof payload.settings === 'object') {
        vault.settings = { ...vault.settings, ...payload.settings };
      }
      vault.updatedAt = Date.now();
      return this.getUserState(userId);
    }

    await ensureNeonSchema();
    const previous = await this.getUserState(userId);
    const now = Date.now();
    const favorites = Array.isArray(payload.favoriteIds)
      ? new Set(payload.favoriteIds.map(String))
      : new Set(previous.favoriteIds || []);

    if (Array.isArray(payload.tracks)) {
      const records = payload.tracks
        .filter((track) => track && track.id != null && String(track.id).trim())
        .map((track) => ({
          id: String(track.id),
          title: String(track.title || ''),
          artist: String(track.artist || ''),
          album: String(track.album || ''),
          duration_ms: Math.max(0, Number(track.durationMs ?? track.duration ?? 0) || 0),
          uri: String(track.uri || ''),
          source_type: String(track.sourceType || track.onlineSource || 'LOCAL'),
          is_favorite: Array.isArray(payload.favoriteIds) ? favorites.has(String(track.id)) : Boolean(track.isFavorite),
          added_at: Math.max(0, Number(track.addedDate ?? track.addedAt ?? now) || now),
          updated_at: now
        }));
      await executeNeonHttpSql(
        'WITH incoming AS MATERIALIZED (' +
        'SELECT * FROM jsonb_to_recordset($2::jsonb) AS x(' +
        'id text, title text, artist text, album text, duration_ms bigint, uri text, source_type text, ' +
        'is_favorite boolean, added_at bigint, updated_at bigint)), ' +
        'deleted AS (DELETE FROM public.user_tracks AS existing WHERE existing.user_id = $1 ' +
        'AND NOT EXISTS (SELECT 1 FROM incoming WHERE incoming.id = existing.id) RETURNING id), ' +
        'upserted AS (INSERT INTO public.user_tracks ' +
        '(user_id, id, title, artist, album, duration_ms, uri, source_type, is_favorite, added_at, updated_at) ' +
        'SELECT $1, x.id, x.title, x.artist, x.album, x.duration_ms, x.uri, x.source_type, ' +
        'x.is_favorite, x.added_at, x.updated_at FROM incoming AS x ' +
        'ON CONFLICT (user_id, id) DO UPDATE SET title=EXCLUDED.title, artist=EXCLUDED.artist, ' +
        'album=EXCLUDED.album, duration_ms=EXCLUDED.duration_ms, uri=EXCLUDED.uri, ' +
        'source_type=EXCLUDED.source_type, is_favorite=EXCLUDED.is_favorite, ' +
        'added_at=EXCLUDED.added_at, updated_at=EXCLUDED.updated_at RETURNING id) ' +
        'SELECT (SELECT COUNT(*) FROM deleted) AS deleted_count, ' +
        '(SELECT COUNT(*) FROM upserted) AS upserted_count',
        [userId, JSON.stringify(records)]
      );
    }

    if (Array.isArray(payload.playlists)) {
      const records = payload.playlists
        .filter((playlist) => playlist && playlist.id != null && String(playlist.id).trim())
        .map((playlist) => ({
          id: String(playlist.id),
          name: String(playlist.name || ''),
          song_ids_json: Array.isArray(playlist.songIds) ? playlist.songIds.map(String) : [],
          created_at: Math.max(0, Number(playlist.createdAt || now) || now),
          updated_at: now
        }));
      await executeNeonHttpSql(
        'WITH incoming AS MATERIALIZED (' +
        'SELECT * FROM jsonb_to_recordset($2::jsonb) AS x(id text, name text, song_ids_json jsonb, created_at bigint, updated_at bigint)), ' +
        'deleted AS (DELETE FROM public.user_playlists AS existing WHERE existing.user_id = $1 ' +
        'AND NOT EXISTS (SELECT 1 FROM incoming WHERE incoming.id = existing.id) RETURNING id), ' +
        'upserted AS (INSERT INTO public.user_playlists (user_id, id, name, song_ids_json, created_at, updated_at) ' +
        'SELECT $1, x.id, x.name, x.song_ids_json, x.created_at, x.updated_at FROM incoming AS x ' +
        'ON CONFLICT (user_id, id) DO UPDATE SET name=EXCLUDED.name, ' +
        'song_ids_json=EXCLUDED.song_ids_json, updated_at=EXCLUDED.updated_at RETURNING id) ' +
        'SELECT (SELECT COUNT(*) FROM deleted) AS deleted_count, ' +
        '(SELECT COUNT(*) FROM upserted) AS upserted_count',
        [userId, JSON.stringify(records)]
      );
    }

    const settings = {
      theme: payload.themeName || payload.settings?.theme || previous.settings?.theme || 'DARK',
      languageMode: payload.languageMode || payload.settings?.languageMode || previous.settings?.languageMode || 'auto',
      eqState: payload.settings?.eqState || payload.eqState || previous.settings?.eqState || {},
      customPresets: payload.settings?.customPresets || payload.customPresets || previous.settings?.customPresets || []
    };
    const settingsChanged = Boolean(payload.themeName || payload.languageMode || payload.settings ||
      payload.eqState || payload.customPresets);
    if (settingsChanged) {
      await executeNeonHttpSql(
        'INSERT INTO public.user_settings (user_id, theme, language_mode, eq_state_json, custom_presets_json, updated_at) ' +
        'VALUES ($1, $2, $3, $4::jsonb, $5::jsonb, $6) ' +
        'ON CONFLICT (user_id) DO UPDATE SET theme=EXCLUDED.theme, language_mode=EXCLUDED.language_mode, ' +
        'eq_state_json=EXCLUDED.eq_state_json, custom_presets_json=EXCLUDED.custom_presets_json, updated_at=EXCLUDED.updated_at',
        [userId, settings.theme, settings.languageMode, JSON.stringify(settings.eqState), JSON.stringify(settings.customPresets), now]
      );
    }
    return this.getUserState(userId);
  }

  async updatePlaybackState(userId, payload) {
    if (this.testMode) {
      const vault = this.ensureUserVault(userId);
      const prior = vault.playbackState;
      const incomingDeviceId = String(payload.activeDeviceId || payload.deviceId || 'web_default');
      const incomingPlatform = String(payload.activeDevicePlatform || payload.platform || 'web');
      const isPlaying = Boolean(payload.isPlaying);
      const updated = {
        userId,
        activeDeviceId: isPlaying ? incomingDeviceId : (prior.activeDeviceId || incomingDeviceId),
        activeDevicePlatform: isPlaying ? incomingPlatform : (prior.activeDevicePlatform || incomingPlatform),
        isPlaying,
        currentTrackId: String(payload.currentTrackId ?? prior.currentTrackId ?? ''),
        currentTrackTitle: String(payload.currentTrackTitle ?? prior.currentTrackTitle ?? ''),
        currentTrackArtist: String(payload.currentTrackArtist ?? prior.currentTrackArtist ?? ''),
        currentTrackUri: String(payload.currentTrackUri ?? prior.currentTrackUri ?? ''),
        positionMs: Math.max(0, Number(payload.positionMs ?? prior.positionMs ?? 0) || 0),
        playbackMode: String(payload.playbackMode ?? prior.playbackMode ?? 'NORMAL'),
        queueIds: Array.isArray(payload.queueIds) ? payload.queueIds.map(String) : (prior.queueIds || []),
        updatedAt: Date.now(),
        volume: Math.min(1, Math.max(0, Number(payload.volume ?? prior.volume ?? 0.85)))
      };
      vault.playbackState = updated;
      vault.updatedAt = updated.updatedAt;
      return {
        playbackState: updated, previousActiveDevice: prior.activeDeviceId,
        outputTransferred: Boolean(isPlaying && prior.activeDeviceId && prior.activeDeviceId !== incomingDeviceId)
      };
    }

    await ensureNeonSchema();
    const previous = await this.getUserState(userId);
    const prior = previous.playbackState;
    const incomingDeviceId = String(payload.activeDeviceId || payload.deviceId || 'web_default');
    const incomingPlatform = String(payload.activeDevicePlatform || payload.platform || 'web');
    const isPlaying = Boolean(payload.isPlaying);
    const updated = {
      userId,
      activeDeviceId: isPlaying ? incomingDeviceId : (prior.activeDeviceId || incomingDeviceId),
      activeDevicePlatform: isPlaying ? incomingPlatform : (prior.activeDevicePlatform || incomingPlatform),
      isPlaying,
      currentTrackId: String(payload.currentTrackId ?? prior.currentTrackId ?? ''),
      currentTrackTitle: String(payload.currentTrackTitle ?? prior.currentTrackTitle ?? ''),
      currentTrackArtist: String(payload.currentTrackArtist ?? prior.currentTrackArtist ?? ''),
      currentTrackUri: String(payload.currentTrackUri ?? prior.currentTrackUri ?? ''),
      positionMs: Math.max(0, Number(payload.positionMs ?? prior.positionMs ?? 0) || 0),
      playbackMode: String(payload.playbackMode ?? prior.playbackMode ?? 'NORMAL'),
      queueIds: Array.isArray(payload.queueIds) ? payload.queueIds.map(String) : (prior.queueIds || []),
      updatedAt: Date.now(),
      volume: Math.min(1, Math.max(0, Number(payload.volume ?? prior.volume ?? 0.85)))
    };
    await executeNeonHttpSql(
      'INSERT INTO public.user_playback_state ' +
      '(user_id, active_device_id, active_device_platform, is_playing, current_track_json, queue_json, position_ms, playback_mode, volume, updated_at) ' +
      'VALUES ($1, $2, $3, $4, $5::jsonb, $6::jsonb, $7, $8, $9, $10) ' +
      'ON CONFLICT (user_id) DO UPDATE SET active_device_id=EXCLUDED.active_device_id, ' +
      'active_device_platform=EXCLUDED.active_device_platform, is_playing=EXCLUDED.is_playing, ' +
      'current_track_json=EXCLUDED.current_track_json, queue_json=EXCLUDED.queue_json, ' +
      'position_ms=EXCLUDED.position_ms, playback_mode=EXCLUDED.playback_mode, ' +
      'volume=EXCLUDED.volume, updated_at=EXCLUDED.updated_at',
      [
        userId, updated.activeDeviceId, updated.activeDevicePlatform, updated.isPlaying,
        JSON.stringify({ id: updated.currentTrackId, title: updated.currentTrackTitle, artist: updated.currentTrackArtist, uri: updated.currentTrackUri }),
        JSON.stringify(updated.queueIds), updated.positionMs, updated.playbackMode, updated.volume, updated.updatedAt
      ]
    );
    return {
      playbackState: updated,
      previousActiveDevice: prior.activeDeviceId,
      outputTransferred: Boolean(isPlaying && prior.activeDeviceId && prior.activeDeviceId !== incomingDeviceId)
    };
  }

}

export const neonCloudStore = new NeonCloudStore();

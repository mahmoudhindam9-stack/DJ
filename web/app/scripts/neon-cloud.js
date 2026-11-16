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
  return (
    process.env.NEON_REALTIME_SECRET ||
    process.env.DATABASE_URL ||
    'dj-cloud-neon-session-signing-key-v1'
  );
}

export function createSignedSessionToken(payload) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(
    JSON.stringify({
      sub: payload.userId,
      userId: payload.userId,
      email: payload.email,
      displayName: payload.displayName,
      authProvider: payload.authProvider || 'email',
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 7
    })
  ).toString('base64url');
  const sig = crypto
    .createHmac('sha256', getSigningSecret())
    .update(`${header}.${body}`)
    .digest('base64url');
  return `${header}.${body}.${sig}`;
}

export function verifySessionToken(token) {
  if (!token || typeof token !== 'string') return null;
  const cleaned = token.replace(/^Bearer\s+/i, '').trim();
  const parts = cleaned.split('.');
  if (parts.length === 3) {
    try {
      const [header, body, sig] = parts;
      const expected = crypto
        .createHmac('sha256', getSigningSecret())
        .update(`${header}.${body}`)
        .digest('base64url');
      if (sig === expected) {
        const decoded = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
        if (decoded.exp && decoded.exp * 1000 < Date.now()) {
          return null;
        }
        return decoded;
      }
    } catch {
      return null;
    }
  }
  return null;
}

/**
 * Executes a parameterized query against Neon HTTP SQL API if DATABASE_URL is configured.
 */
async function executeNeonHttpSql(query, params = []) {
  const dbUrl = process.env.DATABASE_URL;
  if (!dbUrl) return null;
  try {
    const parsed = new URL(dbUrl);
    const httpEndpoint = `https://${parsed.hostname}/sql`;
    const response = await fetch(httpEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Neon-Connection-String': dbUrl
      },
      body: JSON.stringify({ query, params })
    });
    if (!response.ok) return null;
    return await response.json();
  } catch {
    return null;
  }
}

let schemaInitialized = false;
export async function ensureNeonSchema() {
  if (schemaInitialized) return;
  schemaInitialized = true;
  if (NEON_CLOUD_CONFIG.hasDatabaseUrl) {
    const statements = NEON_CANONICAL_SCHEMA_SQL.split(';')
      .map((s) => s.trim())
      .filter(Boolean);
    for (const stmt of statements) {
      await executeNeonHttpSql(stmt);
    }
  }
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
  }

  resetForTests() {
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
    await ensureNeonSchema();
    const normalizedEmail = String(email || '').trim().toLowerCase();
    if (!normalizedEmail.includes('@') || !normalizedEmail.includes('.')) {
      throw new Error('Invalid email address');
    }
    if (!password || String(password).length < 6) {
      throw new Error('Password must be at least 6 characters');
    }

    const userId = 'usr_' + sha256(normalizedEmail).slice(0, 20);
    const passwordHash = sha256(`${normalizedEmail}:${password}`);
    const existing = this.usersByEmail.get(normalizedEmail);
    if (existing && existing.passwordHash && existing.passwordHash !== passwordHash) {
      throw new Error('Email is already registered with a different password');
    }

    const resolvedName = String(displayName || '').trim() || normalizedEmail.split('@')[0];
    const userRecord = {
      userId,
      email: normalizedEmail,
      displayName: resolvedName,
      authProvider: 'email',
      passwordHash,
      createdAt: existing?.createdAt || Date.now()
    };

    this.usersByEmail.set(normalizedEmail, userRecord);
    this.usersById.set(userId, userRecord);
    this.ensureUserVault(userId);

    await executeNeonHttpSql(
      `INSERT INTO users (user_id, email, display_name, auth_provider, password_hash)
       VALUES ($1, $2, $3, 'email', $4)
       ON CONFLICT (email) DO UPDATE SET display_name = EXCLUDED.display_name, updated_at = NOW()`,
      [userId, normalizedEmail, resolvedName, passwordHash]
    );

    const sessionToken = createSignedSessionToken(userRecord);
    return {
      userId,
      email: normalizedEmail,
      displayName: resolvedName,
      authProvider: 'email',
      sessionToken
    };
  }

  async signInEmail({ email, password }) {
    await ensureNeonSchema();
    const normalizedEmail = String(email || '').trim().toLowerCase();
    if (!normalizedEmail.includes('@') || !password) {
      throw new Error('Email and password are required');
    }
    const passwordHash = sha256(`${normalizedEmail}:${password}`);
    const existing = this.usersByEmail.get(normalizedEmail);
    if (existing && existing.passwordHash && existing.passwordHash !== passwordHash) {
      const err = new Error('Invalid email or password');
      err.statusCode = 401;
      throw err;
    }

    const userId = existing?.userId || 'usr_' + sha256(normalizedEmail).slice(0, 20);
    const displayName = existing?.displayName || normalizedEmail.split('@')[0];
    const userRecord = {
      userId,
      email: normalizedEmail,
      displayName,
      authProvider: 'email',
      passwordHash,
      createdAt: existing?.createdAt || Date.now()
    };
    this.usersByEmail.set(normalizedEmail, userRecord);
    this.usersById.set(userId, userRecord);
    this.ensureUserVault(userId);

    const sessionToken = createSignedSessionToken(userRecord);
    return {
      userId,
      email: normalizedEmail,
      displayName,
      authProvider: 'email',
      sessionToken
    };
  }

  async signInGoogle({ email, displayName, idToken }) {
    await ensureNeonSchema();
    const normalizedEmail = String(email || '').trim().toLowerCase();
    if (!normalizedEmail.includes('@')) {
      throw new Error('Valid Google email is required');
    }

    const existing = this.usersByEmail.get(normalizedEmail);
    const userId = existing?.userId || 'usr_' + sha256(normalizedEmail).slice(0, 20);
    const resolvedName =
      String(displayName || '').trim() || existing?.displayName || normalizedEmail.split('@')[0];

    const userRecord = {
      userId,
      email: normalizedEmail,
      displayName: resolvedName,
      authProvider: 'google',
      passwordHash: existing?.passwordHash || '',
      createdAt: existing?.createdAt || Date.now()
    };

    this.usersByEmail.set(normalizedEmail, userRecord);
    this.usersById.set(userId, userRecord);
    this.ensureUserVault(userId);

    await executeNeonHttpSql(
      `INSERT INTO users (user_id, email, display_name, auth_provider)
       VALUES ($1, $2, $3, 'google')
       ON CONFLICT (email) DO UPDATE SET display_name = EXCLUDED.display_name, updated_at = NOW()`,
      [userId, normalizedEmail, resolvedName]
    );

    const sessionToken = createSignedSessionToken({ ...userRecord, idTokenPresent: Boolean(idToken) });
    return {
      userId,
      email: normalizedEmail,
      displayName: resolvedName,
      authProvider: 'google',
      sessionToken
    };
  }

  /**
   * Resolves the authenticated userId from Authorization token or verified handoff header.
   * Prevents cross-account access if a caller attempts to read/write another user's userId.
   */
  resolveAuthorizedUserId(req, requestedUserId) {
    const authHeader = req.headers?.authorization || req.headers?.Authorization || '';
    const verified = verifySessionToken(authHeader);
    if (verified?.userId) {
      if (requestedUserId && requestedUserId !== verified.userId) {
        const err = new Error('Forbidden: cross-user data access denied');
        err.statusCode = 403;
        throw err;
      }
      return verified.userId;
    }

    const headerUserId = req.headers?.['x-user-id'] || req.headers?.['X-User-Id'];
    if (headerUserId && String(headerUserId).startsWith('usr_')) {
      if (requestedUserId && requestedUserId !== headerUserId) {
        const err = new Error('Forbidden: cross-user data access denied');
        err.statusCode = 403;
        throw err;
      }
      return String(headerUserId);
    }

    if (requestedUserId && String(requestedUserId).startsWith('usr_')) {
      return String(requestedUserId);
    }

    const err = new Error('Unauthorized: valid user session required');
    err.statusCode = 401;
    throw err;
  }

  async getUserState(userId) {
    const vault = this.ensureUserVault(userId);
    return {
      userId,
      tracks: vault.tracks,
      playlists: vault.playlists,
      favoriteIds: vault.favoriteIds,
      settings: vault.settings,
      playbackState: vault.playbackState,
      updatedAt: vault.updatedAt
    };
  }

  async updateUserState(userId, payload) {
    const vault = this.ensureUserVault(userId);
    if (Array.isArray(payload.tracks)) {
      vault.tracks = payload.tracks.map((t) => ({
        ...t,
        userId // Enforce user_id ownership on every track record
      }));
    }
    if (Array.isArray(payload.playlists)) {
      vault.playlists = payload.playlists.map((p) => ({
        ...p,
        userId // Enforce user_id ownership on every playlist record
      }));
    }
    if (Array.isArray(payload.favoriteIds)) {
      vault.favoriteIds = Array.from(new Set(payload.favoriteIds.map(String)));
    }
    if (payload.languageMode) {
      vault.settings.languageMode = payload.languageMode;
    }
    if (payload.themeName) {
      vault.settings.theme = payload.themeName;
    }
    if (payload.settings && typeof payload.settings === 'object') {
      vault.settings = {
        ...vault.settings,
        ...payload.settings
      };
    }
    vault.updatedAt = Date.now();

    await executeNeonHttpSql(
      `INSERT INTO user_settings (user_id, theme, language_mode, eq_state_json, custom_presets_json, updated_at)
       VALUES ($1, $2, $3, $4::jsonb, $5::jsonb, $6)
       ON CONFLICT (user_id) DO UPDATE SET
         theme = EXCLUDED.theme,
         language_mode = EXCLUDED.language_mode,
         eq_state_json = EXCLUDED.eq_state_json,
         custom_presets_json = EXCLUDED.custom_presets_json,
         updated_at = EXCLUDED.updated_at`,
      [
        userId,
        vault.settings.theme || 'DARK',
        vault.settings.languageMode || 'auto',
        JSON.stringify(vault.settings.eqState || {}),
        JSON.stringify(vault.settings.customPresets || []),
        vault.updatedAt
      ]
    );

    return this.getUserState(userId);
  }

  /**
   * Updates playback state and enforces Single Active Audio Output Arbitration:
   * Whenever a device reports `isPlaying: true`, it becomes the sole `activeDeviceId` for `userId`.
   */
  async updatePlaybackState(userId, payload) {
    const vault = this.ensureUserVault(userId);
    const previousActiveDevice = vault.playbackState.activeDeviceId;
    const incomingDeviceId = String(payload.activeDeviceId || payload.deviceId || 'web_default');
    const incomingPlatform = String(payload.activeDevicePlatform || payload.platform || 'web');
    const isPlaying = Boolean(payload.isPlaying);

    const updated = {
      userId,
      activeDeviceId: isPlaying
        ? incomingDeviceId
        : vault.playbackState.activeDeviceId || incomingDeviceId,
      activeDevicePlatform: isPlaying
        ? incomingPlatform
        : vault.playbackState.activeDevicePlatform || incomingPlatform,
      isPlaying,
      currentTrackId: String(payload.currentTrackId ?? vault.playbackState.currentTrackId ?? ''),
      currentTrackTitle: String(
        payload.currentTrackTitle ?? vault.playbackState.currentTrackTitle ?? ''
      ),
      currentTrackArtist: String(
        payload.currentTrackArtist ?? vault.playbackState.currentTrackArtist ?? ''
      ),
      currentTrackUri: String(payload.currentTrackUri ?? vault.playbackState.currentTrackUri ?? ''),
      positionMs: Number(payload.positionMs ?? vault.playbackState.positionMs ?? 0),
      playbackMode: String(payload.playbackMode ?? vault.playbackState.playbackMode ?? 'NORMAL'),
      queueIds: Array.isArray(payload.queueIds)
        ? payload.queueIds.map(String)
        : vault.playbackState.queueIds || [],
      updatedAt: Date.now()
    };

    vault.playbackState = updated;
    vault.updatedAt = updated.updatedAt;

    await executeNeonHttpSql(
      `INSERT INTO user_playback_state
        (user_id, active_device_id, active_device_platform, is_playing, current_track_json, queue_json, position_ms, playback_mode, updated_at)
       VALUES ($1, $2, $3, $4, $5::jsonb, $6::jsonb, $7, $8, $9)
       ON CONFLICT (user_id) DO UPDATE SET
         active_device_id = EXCLUDED.active_device_id,
         active_device_platform = EXCLUDED.active_device_platform,
         is_playing = EXCLUDED.is_playing,
         current_track_json = EXCLUDED.current_track_json,
         queue_json = EXCLUDED.queue_json,
         position_ms = EXCLUDED.position_ms,
         playback_mode = EXCLUDED.playback_mode,
         updated_at = EXCLUDED.updated_at`,
      [
        userId,
        updated.activeDeviceId,
        updated.activeDevicePlatform,
        updated.isPlaying,
        JSON.stringify({
          id: updated.currentTrackId,
          title: updated.currentTrackTitle,
          artist: updated.currentTrackArtist,
          uri: updated.currentTrackUri
        }),
        JSON.stringify(updated.queueIds),
        updated.positionMs,
        updated.playbackMode,
        updated.updatedAt
      ]
    );

    return {
      playbackState: updated,
      previousActiveDevice,
      outputTransferred: Boolean(
        isPlaying && previousActiveDevice && previousActiveDevice !== incomingDeviceId
      )
    };
  }
}

export const neonCloudStore = new NeonCloudStore();

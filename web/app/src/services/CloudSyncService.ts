import { AudioTrack, CloudPlaybackState, CloudUser, Playlist } from '../types';

const STORAGE_SESSION_KEY = 'dj_cloud_session';
const STORAGE_DEVICE_ID_KEY = 'dj_web_device_id';

export const NEON_WEB_ENDPOINTS = {
  projectId: 'dark-fire-99620103',
  branchId: 'br-spring-term-baakf0qx',
  authUrl:
    (import.meta as any).env?.VITE_NEON_AUTH_URL ||
    'https://ep-morning-bird-bad5xn0x.neonauth.c-8.eu-central-1.aws.neon.tech/neondb/auth',
  realtimeUrl:
    (import.meta as any).env?.VITE_NEON_REALTIME_URL ||
    'https://ep-morning-bird-bad5xn0x.rt.c-8.eu-central-1.aws.neon.tech/'
};

type SyncListener = (state: {
  user: CloudUser | null;
  playbackState: CloudPlaybackState | null;
  status: string;
}) => void;

class CloudSyncService {
  private currentUser: CloudUser | null = null;
  private playbackState: CloudPlaybackState | null = null;
  private status = 'Ready';
  private deviceId = '';
  private pollTimer: number | null = null;
  private listeners = new Set<SyncListener>();
  private onRemoteClaimedOutput: ((playback: CloudPlaybackState) => void) | null = null;
  private onUserChanged: ((user: CloudUser | null) => void | Promise<void>) | null = null;
  private lastVaultSignature = '';
  private onVaultLoaded:
    | ((vault: {
        tracks: AudioTrack[];
        playlists: Array<Playlist & { songIds?: string[] }>;
        favoriteIds: string[];
        languageMode?: 'auto' | 'ar' | 'en';
        themeName?: string;
      }) => void)
    | null = null;

  init(callbacks?: {
    onRemoteAudioClaimed?: (playback: CloudPlaybackState) => void;
    onRemoteOutputClaimed?: (playback: CloudPlaybackState) => void;
    onUserChanged?: (user: CloudUser | null) => void | Promise<void>;
    onVaultLoaded?: (vault: {
      tracks: AudioTrack[];
      playlists: Array<Playlist & { songIds?: string[] }>;
      favoriteIds: string[];
      languageMode?: 'auto' | 'ar' | 'en';
      themeName?: string;
    }) => void;
  }) {
    const audioCallback = callbacks?.onRemoteAudioClaimed || callbacks?.onRemoteOutputClaimed;
    if (audioCallback) this.onRemoteClaimedOutput = audioCallback;
    if (callbacks?.onUserChanged) this.onUserChanged = callbacks.onUserChanged;
    if (callbacks?.onVaultLoaded) {
      this.onVaultLoaded = callbacks.onVaultLoaded;
    }

    let devId = localStorage.getItem(STORAGE_DEVICE_ID_KEY);
    if (!devId) {
      devId = 'web_' + Math.random().toString(36).slice(2, 12);
      localStorage.setItem(STORAGE_DEVICE_ID_KEY, devId);
    }
    this.deviceId = devId;

    // 1. Check existing saved session in localStorage
    const rawSession = localStorage.getItem(STORAGE_SESSION_KEY);
    if (rawSession) {
      try {
        const parsed = JSON.parse(rawSession);
        if (parsed?.userId && parsed?.email && parsed?.sessionToken) {
          void this.setSession(parsed);
        }
      } catch {
        // ignore invalid JSON
      }
    }

    // 2. Check URL ?handoff= token from Android "Open Web" or embedded WebView
    try {
      const params = new URLSearchParams(window.location.search);
      const handoff = params.get('handoff');
      if (handoff) {
        this.exchangeHandoffToken(handoff);
        // Clean URL parameter without reloading
        params.delete('handoff');
        const nextSearch = params.toString();
        const cleanUrl =
          window.location.pathname + (nextSearch ? `?${nextSearch}` : '') + window.location.hash;
        window.history.replaceState({}, '', cleanUrl);
      }
    } catch {
      // ignore URL parse error
    }

    // 3. Listen for Android WebView session bootstrap event
    window.addEventListener('dj-cloud-session-handoff', ((ev: CustomEvent) => {
      const detail = ev.detail;
      if (detail?.userId && detail?.email && detail?.sessionToken) {
        void this.setSession({
          userId: detail.userId,
          email: detail.email,
          displayName: detail.displayName || detail.email.split('@')[0],
          authProvider: detail.authProvider || 'email',
          sessionToken: detail.sessionToken
        });
      }
    }) as EventListener);
  }

  getDeviceId(): string {
    if (!this.deviceId) {
      this.init();
    }
    return this.deviceId;
  }

  getUser(): CloudUser | null {
    return this.currentUser;
  }

  getPlaybackState(): CloudPlaybackState | null {
    return this.playbackState;
  }

  subscribe(listener: SyncListener): () => void {
    this.listeners.add(listener);
    listener({
      user: this.currentUser,
      playbackState: this.playbackState,
      status: this.status
    });
    return () => {
      this.listeners.delete(listener);
    };
  }

  private emit() {
    for (const listener of this.listeners) {
      listener({
        user: this.currentUser,
        playbackState: this.playbackState,
        status: this.status
      });
    }
  }

  private async setSession(user: CloudUser): Promise<void> {
    this.currentUser = user;
    localStorage.setItem(STORAGE_SESSION_KEY, JSON.stringify(user));
    this.status = `Checking cloud session (${user.email})`;
    this.lastVaultSignature = '';
    this.emit();
    await this.onUserChanged?.(user);
    this.pullRemoteStateOnce();
    this.startPolling();
  }

  async exchangeHandoffToken(handoff: string): Promise<CloudUser | null> {
    try {
      const resp = await fetch('/api/auth/exchange-handoff', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ handoff })
      });
      const json = await resp.json().catch(() => null);
      if (!resp.ok || !json?.userId || !json?.sessionToken) {
        this.status = json?.error || 'Android cloud session could not be verified.';
        this.emit();
        return null;
      }
      const user: CloudUser = {
        userId: json.userId,
        email: json.email,
        displayName: json.displayName,
        authProvider: json.authProvider || 'email',
        sessionToken: json.sessionToken
      };
      await this.setSession(user);
      return user;
    } catch {
      this.status = 'Cloud handoff could not be verified. Connect to the internet and try again.';
      this.emit();
      return null;
    }
  }

  async signUpWithEmail(email: string, password: string, displayName?: string): Promise<CloudUser> {
    const resp = await fetch('/api/auth/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, displayName, deviceId: this.getDeviceId() })
    });
    const json = await resp.json();
    if (!resp.ok || !json?.userId) {
      throw new Error(json?.error || 'Sign-up failed');
    }
    const user: CloudUser = {
      userId: json.userId,
      email: json.email,
      displayName: json.displayName,
      authProvider: 'email',
      sessionToken: json.sessionToken
    };
    await this.setSession(user);
    return user;
  }

  async signInWithEmail(email: string, password: string): Promise<CloudUser> {
    const resp = await fetch('/api/auth/signin', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, deviceId: this.getDeviceId() })
    });
    const json = await resp.json();
    if (!resp.ok || !json?.userId) {
      throw new Error(json?.error || 'Sign-in failed');
    }
    const user: CloudUser = {
      userId: json.userId,
      email: json.email,
      displayName: json.displayName,
      authProvider: 'email',
      sessionToken: json.sessionToken
    };
    await this.setSession(user);
    return user;
  }

  async signInWithGoogle(idToken: string): Promise<CloudUser> {
    const resp = await fetch('/api/auth/google', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken, deviceId: this.getDeviceId() })
    });
    const json = await resp.json().catch(() => null);
    if (!resp.ok || !json?.userId || !json?.sessionToken) {
      throw new Error(json?.error || 'Google Sign-In failed');
    }
    const user: CloudUser = {
      userId: json.userId,
      email: json.email,
      displayName: json.displayName,
      authProvider: 'google',
      sessionToken: json.sessionToken
    };
    await this.setSession(user);
    return user;
  }

  async signOut(): Promise<void> {
    if (this.pollTimer !== null) {
      window.clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    localStorage.removeItem(STORAGE_SESSION_KEY);
    this.currentUser = null;
    this.playbackState = null;
    this.status = 'Signed out';
    this.lastVaultSignature = '';
    this.emit();
    await this.onUserChanged?.(null);
  }

  async claimWebAudioOutput(params: {
    isPlaying: boolean;
    currentTrack?: AudioTrack | null;
    positionMs?: number;
    playbackMode?: string;
    queueIds?: string[];
  }): Promise<CloudPlaybackState | null> {
    const user = this.currentUser;
    if (!user) return null;
    const payload: CloudPlaybackState = {
      userId: user.userId,
      activeDeviceId: this.getDeviceId(),
      activeDevicePlatform: 'web',
      isPlaying: params.isPlaying,
      currentTrackId: params.currentTrack?.id || '',
      currentTrackTitle: params.currentTrack?.title || '',
      currentTrackArtist: params.currentTrack?.artist || '',
      currentTrackUri: params.currentTrack?.uri || '',
      positionMs: Math.round(params.positionMs || 0),
      playbackMode: params.playbackMode || 'NORMAL',
      queueIds: params.queueIds || [],
      updatedAt: Date.now()
    };
    this.playbackState = payload;
    this.emit();

    try {
      const resp = await fetch('/api/sync/playback', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${user.sessionToken}`,
          'X-User-Id': user.userId
        },
        body: JSON.stringify(payload)
      });
      if (resp.ok) {
        const json = await resp.json();
        if (json?.playbackState) {
          this.playbackState = json.playbackState;
          this.emit();
        }
      }
    } catch {
      // ignore offline
    }
    return this.playbackState;
  }

  async pushUserVaultState(params: {
    tracks?: AudioTrack[];
    playlists?: Playlist[];
    favoriteIds?: string[];
    languageMode?: 'auto' | 'ar' | 'en';
    themeName?: string;
  }) {
    const user = this.currentUser;
    if (!user) return;
    try {
      const response = await fetch('/api/sync/state', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${user.sessionToken}`,
          'X-User-Id': user.userId
        },
        body: JSON.stringify({
          userId: user.userId,
          ...params
        })
      });
      if (!response.ok) {
        const payload = await response.json().catch(() => null);
        this.status = payload?.error || ('Cloud save failed (' + response.status + ').');
        this.emit();
      }
    } catch {
      this.status = 'Cloud save failed. Check your connection.';
      this.emit();
    }
  }

  async pullRemoteStateOnce() {
    const user = this.currentUser;
    if (!user) return;
    try {
      const resp = await fetch(`/api/sync/state?userId=${encodeURIComponent(user.userId)}`, {
        headers: {
          Authorization: `Bearer ${user.sessionToken}`,
          'X-User-Id': user.userId
        }
      });
      if (resp.status === 401 || resp.status === 403) {
        localStorage.removeItem(STORAGE_SESSION_KEY);
        this.currentUser = null;
        this.playbackState = null;
        this.status = 'Cloud session expired or invalid. Please sign in again.';
        this.lastVaultSignature = '';
        this.emit();
        void this.onUserChanged?.(null);
        if (this.pollTimer !== null) window.clearInterval(this.pollTimer);
        this.pollTimer = null;
        return;
      }
      if (!resp.ok) {
        this.status = 'Cloud sync request failed (' + resp.status + ').';
        this.emit();
        return;
      }
      const json = await resp.json();
      if (this.currentUser?.userId !== user.userId) return;
      if (json?.userId && json.userId === user.userId) {
        if (json.playbackState) {
          const remotePb: CloudPlaybackState = json.playbackState;
          const prevUpdatedAt = this.playbackState?.updatedAt || 0;
          this.playbackState = remotePb;
          // Single Active Audio Output Arbitration:
          // If another device (e.g., Android) is now actively playing, immediately pause Web audio!
          if (
            remotePb.activeDeviceId &&
            remotePb.activeDeviceId !== this.getDeviceId() &&
            remotePb.isPlaying &&
            remotePb.updatedAt > prevUpdatedAt
          ) {
            this.status = `Audio active on ${remotePb.activeDevicePlatform.toUpperCase()}`;
            this.onRemoteClaimedOutput?.(remotePb);
            window.dispatchEvent(new CustomEvent('dj-remote-audio-claimed', { detail: remotePb }));
          }
          this.emit();
        }
        const remoteVault = {
          tracks: Array.isArray(json.tracks) ? json.tracks : [],
          playlists: Array.isArray(json.playlists) ? json.playlists : [],
          favoriteIds: Array.isArray(json.favoriteIds) ? json.favoriteIds : [],
          languageMode: json.settings?.languageMode,
          themeName: json.settings?.themeName || json.settings?.theme
        };
        const signature = JSON.stringify({
          tracks: remoteVault.tracks,
          playlists: remoteVault.playlists,
          favoriteIds: remoteVault.favoriteIds,
          settings: json.settings || {}
        });
        if (this.onVaultLoaded && signature !== this.lastVaultSignature) {
          this.lastVaultSignature = signature;
          this.onVaultLoaded(remoteVault);
        }
        this.status = 'Synced (' + user.email + ')';
        this.emit();
      }
    } catch {
      // ignore
    }
  }

  private startPolling() {
    if (this.pollTimer !== null) {
      window.clearInterval(this.pollTimer);
    }
    this.pollTimer = window.setInterval(() => {
      this.pullRemoteStateOnce();
    }, 3500);
  }
}

export const cloudSyncService = new CloudSyncService();

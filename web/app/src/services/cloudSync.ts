import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  createRealtimeClient,
  type SealedLiveQuery,
  type RawLiveQueryUpdate,
} from '@neon/realtime/client';
import { cloudAuthClient, type CloudUser } from './cloudAuth';

export interface CloudDevice {
  device_id: string;
  name: string;
  platform: 'android' | 'web' | 'desktop';
  last_seen_at: string;
  online: boolean;
}

export interface CloudPlaybackState {
  user_id: string;
  active_device_id: string | null;
  current_track: any | null;
  queue: any[];
  is_playing: boolean;
  position_ms: string | number;
  duration_ms: string | number;
  playback_mode: string;
  revision: string | number;
  updated_at: string;
  active_device_name?: string | null;
}

export interface CloudPlaybackUpdate {
  current_track: any | null;
  queue: any[];
  is_playing: boolean;
  position_ms: number;
  duration_ms: number;
  playback_mode: string;
}

export interface CloudDeviceCommand {
  command_id: string;
  target_device_id: string;
  source_device_id: string;
  command_type: 'pause' | 'switch';
  payload: { next_device_id?: string };
  created_at: string;
}

export interface CloudClaimResult {
  status: 'active' | 'handoff_pending';
  player?: CloudPlaybackState;
  command_id?: string;
  active_device_id?: string;
}

type CloudDeviceCommandHandler = (command: CloudDeviceCommand) => Promise<number | void>;

interface ApiResult<T> {
  ok: boolean;
  data?: T;
  error?: string;
}

const DEVICE_STORAGE_KEY = 'dj-cloud-device-id';
const realtimeUrl = import.meta.env.VITE_NEON_REALTIME_URL;
const realtimeClient = typeof realtimeUrl === 'string' && realtimeUrl
  ? createRealtimeClient({ url: realtimeUrl })
  : null;

function getLocalDeviceId(): string {
  try {
    const saved = window.localStorage.getItem(DEVICE_STORAGE_KEY);
    if (saved && /^[0-9a-f-]{36}$/i.test(saved)) return saved;
    const created = crypto.randomUUID();
    window.localStorage.setItem(DEVICE_STORAGE_KEY, created);
    return created;
  } catch {
    return crypto.randomUUID();
  }
}

function describeLocalDevice(): { name: string; platform: CloudDevice['platform'] } {
  const ua = navigator.userAgent || '';
  const isAndroid = /Android/i.test(ua);
  const isTauri = Boolean((window as any).__TAURI_INTERNALS__);
  const platform = isAndroid ? 'android' : isTauri ? 'desktop' : 'web';
  const name = isAndroid
    ? 'Android device'
    : isTauri
    ? 'DJ Desktop'
    : /Macintosh|Mac OS X/i.test(ua)
    ? 'Mac browser'
    : /Windows/i.test(ua)
    ? 'Windows browser'
    : /Linux/i.test(ua)
    ? 'Linux browser'
    : 'Web browser';
  return { name, platform };
}

async function getBearerToken(): Promise<string> {
  if (!cloudAuthClient) throw new Error('DJ Cloud authentication is not configured.');
  const result = await cloudAuthClient.getSession();
  if (result.error || !result.data?.session?.token) {
    throw new Error(result.error?.message || 'Your DJ Cloud session has expired. Please sign in again.');
  }
  return result.data.session.token;
}

async function cloudRequest<T>(route: string, method = 'GET', body?: unknown): Promise<T> {
  const token = await getBearerToken();
  const response = await fetch('/api/cloud/' + route, {
    method,
    headers: {
      Authorization: 'Bearer ' + token,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: 'no-store',
  });

  let payload: ApiResult<T>;
  try {
    payload = await response.json();
  } catch {
    throw new Error('DJ Cloud returned an invalid response.');
  }
  if (!response.ok || !payload.ok) {
    throw new Error(payload.error || 'DJ Cloud request failed (' + response.status + ').');
  }
  return payload.data as T;
}

export function useCloudSync(
  user: CloudUser | null,
  onPlaybackState?: (state: CloudPlaybackState | null) => void,
  onDeviceCommand?: CloudDeviceCommandHandler
) {
  const deviceId = useMemo(() => getLocalDeviceId(), []);
  const callbackRef = useRef(onPlaybackState);
  callbackRef.current = onPlaybackState;
  const commandHandlerRef = useRef(onDeviceCommand);
  commandHandlerRef.current = onDeviceCommand;
  const [devices, setDevices] = useState<CloudDevice[]>([]);
  const [state, setState] = useState<CloudPlaybackState | null>(null);
  const [status, setStatus] = useState<'disabled' | 'connecting' | 'live' | 'offline' | 'error'>(
    user ? 'connecting' : 'disabled'
  );
  const [error, setError] = useState<string | null>(null);
  const [deviceRegistered, setDeviceRegistered] = useState(false);

  const applyState = useCallback((next: CloudPlaybackState | null) => {
    setState(next);
    callbackRef.current?.(next);
  }, []);

  const refreshDevices = useCallback(async () => {
    const items = await cloudRequest<CloudDevice[]>('devices');
    setDevices(items);
    return items;
  }, []);

  const publishPlayback = useCallback(async (update: CloudPlaybackUpdate) => {
    if (!deviceRegistered) return false;
    try {
      const result = await cloudRequest<{ revision: string; txid: string }>('state', 'PUT', {
        device_id: deviceId,
        ...update,
      });
      return Boolean(result?.revision);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Playback sync failed.';
      if (message.includes('does not own playback control')) return false;
      setError(message);
      return false;
    }
  }, [deviceId, deviceRegistered]);

  const claimPlayback = useCallback(async () => {
    const result = await cloudRequest<CloudClaimResult>('claim', 'POST', { device_id: deviceId });
    if (result.status === 'active' && result.player) applyState(result.player);
    await refreshDevices();
    return result;
  }, [applyState, deviceId, refreshDevices]);

  useEffect(() => {
    if (!user || !cloudAuthClient || !realtimeClient) {
      setStatus('disabled');
      setDeviceRegistered(false);
      applyState(null);
      setDevices([]);
      return;
    }

    let disposed = false;
    let subscription: ReturnType<typeof realtimeClient.subscribe> | null = null;
    let renewalTimer: ReturnType<typeof setTimeout> | null = null;
    let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
    let devicesTimer: ReturnType<typeof setInterval> | null = null;
    let commandsTimer: ReturnType<typeof setInterval> | null = null;
    const processingCommands = new Set<string>();

    const scheduleRenewal = (sealed: SealedLiveQuery<CloudPlaybackState>) => {
      if (renewalTimer) clearTimeout(renewalTimer);
      const delay = Math.max(1000, sealed.expiresAt - Date.now() - 10_000);
      renewalTimer = setTimeout(async () => {
        try {
          const next = await cloudRequest<SealedLiveQuery<CloudPlaybackState>>('state/live', 'POST');
          if (disposed || !subscription) return;
          scheduleRenewal(next);
          await subscription.renew(next);
        } catch (err) {
          if (!disposed) {
            setStatus('error');
            setError(err instanceof Error ? err.message : 'Could not renew live sync.');
          }
        }
      }, delay);
    };

    const start = async () => {
      try {
        setStatus('connecting');
        setError(null);
        const local = describeLocalDevice();
        await cloudRequest('devices/register', 'POST', {
          device_id: deviceId,
          name: local.name,
          platform: local.platform,
        });
        if (disposed) return;
        setDeviceRegistered(true);
        const initial = await cloudRequest<CloudPlaybackState | null>('state');
        if (initial) applyState(initial);
        await refreshDevices();

        const sealed = await cloudRequest<SealedLiveQuery<CloudPlaybackState>>('state/live', 'POST');
        if (disposed) return;
        subscription = realtimeClient.subscribe(sealed);
        const liveRows = new Map<string, CloudPlaybackState>();
        subscription.onUpdate((update) => {
          const typedUpdate = update as RawLiveQueryUpdate<CloudPlaybackState>;
          if (typedUpdate.type === 'rows') {
            liveRows.clear();
            for (const entry of typedUpdate.rows) {
              liveRows.set(entry.rowId, entry.row);
            }
          } else {
            for (const change of typedUpdate.changes) {
              if (change.type === 'upsert') liveRows.set(change.rowId, change.row);
              else liveRows.delete(change.rowId);
            }
          }
          applyState(liveRows.values().next().value || null);
        });
        subscription.onStateChange((nextState) => {
          if (nextState.status === 'live') {
            setStatus('live');
            setError(null);
          } else if (nextState.status === 'stale' || nextState.status === 'connecting') {
            setStatus('connecting');
          } else if (nextState.status === 'error') {
            setStatus('error');
            setError(nextState.error?.message || 'Live sync stopped.');
          }
        });
        scheduleRenewal(sealed);

        heartbeatTimer = setInterval(() => {
          void cloudRequest('devices/heartbeat', 'POST', { device_id: deviceId })
            .catch((err) => {
              if (!disposed) setError(err instanceof Error ? err.message : 'Device heartbeat failed.');
            });
        }, 15_000);
        devicesTimer = setInterval(() => {
          void refreshDevices().catch(() => {});
        }, 15_000);

        const processCommands = async () => {
          if (disposed || processingCommands.size > 0) return;
          try {
            const pending = await cloudRequest<CloudDeviceCommand[]>(
              'commands?device_id=' + encodeURIComponent(deviceId)
            );
            for (const command of pending) {
              if (disposed || processingCommands.has(command.command_id)) continue;
              if (command.command_type !== 'pause') continue;
              const handler = commandHandlerRef.current;
              if (!handler) continue;
              processingCommands.add(command.command_id);
              try {
                // The old output must stop before the server grants control to the next device.
                const positionMs = await handler(command);
                await cloudRequest('commands/ack', 'POST', {
                  device_id: deviceId,
                  command_id: command.command_id,
                  position_ms: Number.isFinite(positionMs) ? Math.max(0, Math.floor(Number(positionMs))) : 0,
                });
              } finally {
                processingCommands.delete(command.command_id);
              }
            }
          } catch (err) {
            if (!disposed) setError(err instanceof Error ? err.message : 'Could not process playback handoff.');
          }
        };
        void processCommands();
        commandsTimer = setInterval(() => { void processCommands(); }, 1_500);
      } catch (err) {
        if (disposed) return;
        setStatus('offline');
        setError(err instanceof Error ? err.message : 'Could not connect to DJ Cloud.');
      }
    };

    void start();
    return () => {
      disposed = true;
      if (renewalTimer) clearTimeout(renewalTimer);
      if (heartbeatTimer) clearInterval(heartbeatTimer);
      if (devicesTimer) clearInterval(devicesTimer);
      if (commandsTimer) clearInterval(commandsTimer);
      subscription?.unsubscribe();
    };
  }, [user?.id, deviceId, applyState, refreshDevices]);

  const fetchLibrary = useCallback(async () => {
    return cloudRequest<Array<{ track_id: string; track: any; is_favorite: boolean; added_at: string; updated_at: string }>>('library');
  }, []);

  const publishLibrary = useCallback(async (tracks: Array<{ id: string; [key: string]: any }>) => {
    const batches = [];
    for (let index = 0; index < tracks.length; index += 500) {
      batches.push(tracks.slice(index, index + 500));
    }
    for (const batch of batches) {
      await cloudRequest('library', 'PUT', {
        tracks: batch.map((track) => ({
          track_id: track.id,
          track,
          is_favorite: Boolean(track.isFavorite),
          added_at: new Date(track.addedDate || Date.now()).toISOString(),
        })),
      });
    }
    return tracks.length;
  }, []);

  const deleteLibraryTrack = useCallback(async (trackId: string) => {
    return cloudRequest<{ deleted: boolean }>('library/' + encodeURIComponent(trackId), 'DELETE');
  }, []);

  const fetchPlaylists = useCallback(async () => {
    return cloudRequest<Array<{
      playlist_id: string;
      name: string;
      created_at: string;
      updated_at: string;
      items: Array<{ track_id: string; position: number; track: any; is_favorite: boolean | null }>;
    }>>('playlists');
  }, []);

  const createCloudPlaylist = useCallback(async (name: string, playlistId?: string) => {
    return cloudRequest<{ playlist_id: string; name: string; created_at: string; updated_at: string }>(
      'playlists',
      'POST',
      { name, playlist_id: playlistId }
    );
  }, []);

  const deleteCloudPlaylist = useCallback(async (playlistId: string) => {
    return cloudRequest<{ deleted: boolean }>('playlists/' + encodeURIComponent(playlistId), 'DELETE');
  }, []);

  const addCloudPlaylistTrack = useCallback(async (playlistId: string, trackId: string, position = 0) => {
    return cloudRequest('playlists/' + encodeURIComponent(playlistId) + '/items', 'POST', {
      track_id: trackId,
      position,
    });
  }, []);

  const removeCloudPlaylistTrack = useCallback(async (playlistId: string, trackId: string) => {
    return cloudRequest(
      'playlists/' + encodeURIComponent(playlistId) + '/items/' + encodeURIComponent(trackId),
      'DELETE'
    );
  }, []);

  const isOwner = Boolean(state?.active_device_id && state.active_device_id === deviceId);
  const activeDevice = devices.find((item) => item.device_id === state?.active_device_id) || null;

  return {
    deviceId,
    devices,
    state,
    status,
    error,
    deviceRegistered,
    isOwner,
    activeDevice,
    refreshDevices,
    publishPlayback,
    claimPlayback,
    fetchLibrary,
    publishLibrary,
    deleteLibraryTrack,
    fetchPlaylists,
    createCloudPlaylist,
    deleteCloudPlaylist,
    addCloudPlaylistTrack,
    removeCloudPlaylistTrack,
  };
}

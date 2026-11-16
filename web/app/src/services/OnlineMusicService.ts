import { AudiusTrack, AudioItem } from '../types';

export interface AlbumatyLink {
  title: string;
  url: string;
}

export interface AlbumatyHomeData {
  categories: AlbumatyLink[];
  albums: AlbumatyLink[];
  songs: AlbumatyLink[];
  artists: AlbumatyLink[];
}

export interface AlbumatySection {
  title: string;
  url: string;
  content: AlbumatyLink[];
}

export interface ResolvedOnlineTrack {
  id: string;
  title: string;
  artist: string;
  album?: string;
  artworkUrl?: string;
  streamUrl: string;
  downloadUrl: string;
  duration?: number;
}

type OnlineApiResponse<T> = {
  ok: boolean;
  data?: T;
  error?: string;
};

const LOCAL_API = '/api/albumaty';
const OLDIES_API = '/api/oldies';

const AUDIUS_NODES = [
  'https://discoveryprovider.audius.co/v1',
  'https://api.audius.co/v1',
];

class OnlineMusicService {
  async getAlbumatyHome(force = false): Promise<AlbumatyHomeData> {
    const suffix = force ? '?force=1' : '';
    return this.requestJson<AlbumatyHomeData>(`${LOCAL_API}/home${suffix}`);
  }

  async getAlbumatySection(url: string): Promise<AlbumatySection> {
    return this.requestJson<AlbumatySection>(
      `${LOCAL_API}/section?url=${encodeURIComponent(url)}`
    );
  }

  async searchAlbumaty(query: string): Promise<AlbumatyHomeData> {
    const term = query.trim();
    if (!term) return { categories: [], albums: [], songs: [], artists: [] };
    return this.requestJson<AlbumatyHomeData>(
      `${LOCAL_API}/search?q=${encodeURIComponent(term)}`
    );
  }

  async resolveAlbumatySong(url: string): Promise<ResolvedOnlineTrack> {
    return this.requestJson<ResolvedOnlineTrack>(
      `${LOCAL_API}/resolve?url=${encodeURIComponent(url)}`
    );
  }

  getAlbumatyStreamEndpoint(url: string): string {
    return `${LOCAL_API}/stream?url=${encodeURIComponent(url)}`;
  }

  getAlbumatyDownloadEndpoint(url: string): string {
    return `${LOCAL_API}/download?url=${encodeURIComponent(url)}`;
  }

  // Oldies & Classics (الزمن الجميل والكلاسيكيات)
  async getOldiesHome(force = false): Promise<AlbumatyHomeData> {
    const suffix = force ? '?force=1' : '';
    return this.requestJson<AlbumatyHomeData>(`${OLDIES_API}/home${suffix}`);
  }

  async getOldiesSection(url: string): Promise<AlbumatySection> {
    return this.requestJson<AlbumatySection>(
      `${OLDIES_API}/section?url=${encodeURIComponent(url)}`
    );
  }

  async searchOldies(query: string): Promise<AlbumatyHomeData> {
    const term = query.trim();
    if (!term) return { categories: [], albums: [], songs: [], artists: [] };
    return this.requestJson<AlbumatyHomeData>(
      `${OLDIES_API}/search?q=${encodeURIComponent(term)}`
    );
  }

  async resolveOldiesSong(url: string): Promise<ResolvedOnlineTrack> {
    return this.requestJson<ResolvedOnlineTrack>(
      `${OLDIES_API}/resolve?url=${encodeURIComponent(url)}`
    );
  }

  getOldiesStreamEndpoint(url: string): string {
    return `${OLDIES_API}/stream?url=${encodeURIComponent(url)}`;
  }

  getOldiesDownloadEndpoint(url: string): string {
    return `${OLDIES_API}/download?url=${encodeURIComponent(url)}`;
  }

  getAudiusStreamEndpoint(url: string): string {
    return `/api/online/stream?url=${encodeURIComponent(url)}`;
  }

  getAudiusDownloadEndpoint(url: string, title = 'song'): string {
    return `/api/online/download?url=${encodeURIComponent(url)}&name=${encodeURIComponent(title)}.mp3`;
  }

  convertResolvedToAudioItem(
    track: ResolvedOnlineTrack,
    stableId?: string,
    onlineSource: 'ALBUMATY' | 'OLDIES' = 'ALBUMATY'
  ): AudioItem {
    const sourceUrl = track.id;
    const streamTarget = track.streamUrl || sourceUrl;
    const downloadTarget = track.downloadUrl || sourceUrl;
    const isOldies = onlineSource === 'OLDIES';
    return {
      id: stableId || `${isOldies ? 'oldies' : 'albumaty'}_${encodeURIComponent(track.id)}`,
      title: track.title,
      artist: track.artist || (isOldies ? 'هبة ميوزيك (الزمن الجميل)' : 'Albumaty'),
      album: track.album || (isOldies ? 'هبة ميوزيك' : 'Albumaty Online'),
      duration: track.duration || 0,
      uri: isOldies
        ? this.getOldiesStreamEndpoint(streamTarget)
        : this.getAlbumatyStreamEndpoint(streamTarget),
      coverUri: track.artworkUrl,
      addedDate: Date.now(),
      onlineSource,
      sourceUrl,
      downloadUri: isOldies
        ? this.getOldiesDownloadEndpoint(downloadTarget)
        : this.getAlbumatyDownloadEndpoint(downloadTarget),
    };
  }

  async getTrendingTracks(): Promise<AudiusTrack[]> {
    return this.fetchAudiusTracks('/tracks/trending?limit=50');
  }

  async getLatestTracks(): Promise<AudiusTrack[]> {
    return this.fetchAudiusTracks('/tracks/latest?limit=50');
  }

  async searchAudius(query: string): Promise<AudiusTrack[]> {
    if (!query.trim()) return [];
    return this.fetchAudiusTracks(
      `/tracks/search?query=${encodeURIComponent(query)}&limit=50&sort_method=relevant`
    );
  }

  convertAudiusToAudioItem(track: AudiusTrack): AudioItem {
    const sourceUrl = track.streamUrl || '';
    return {
      id: `audius_${track.id}`,
      title: track.title,
      artist: track.artist,
      album: track.album || 'Audius',
      duration: track.duration || 0,
      uri: sourceUrl ? this.getAudiusStreamEndpoint(sourceUrl) : '',
      coverUri: track.artworkUrl,
      addedDate: Date.now(),
      onlineSource: 'AUDIUS',
      sourceUrl,
      downloadUri: sourceUrl ? this.getAudiusDownloadEndpoint(sourceUrl, track.title) : undefined,
    };
  }

  private async requestJson<T>(url: string): Promise<T> {
    const response = await fetch(url, {
      headers: { Accept: 'application/json' },
    });

    let payload: OnlineApiResponse<T>;
    try {
      payload = await response.json();
    } catch {
      throw new Error('The online music service returned an invalid response.');
    }

    if (!response.ok || !payload.ok || payload.data === undefined) {
      throw new Error(
        payload.error || `Online music request failed (HTTP ${response.status}).`
      );
    }

    return payload.data;
  }

  private activeAudiusNode = 0;

  private async fetchAudiusTracks(endpoint: string): Promise<AudiusTrack[]> {
    let lastError: unknown = null;

    for (let attempt = 0; attempt < AUDIUS_NODES.length; attempt += 1) {
      const base = AUDIUS_NODES[this.activeAudiusNode % AUDIUS_NODES.length];
      const separator = endpoint.includes('?') ? '&' : '?';
      const url = `${base}${endpoint}${separator}app_name=DJDesktop`;

      try {
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 8000);
        const response = await fetch(url, {
          signal: controller.signal,
          headers: { Accept: 'application/json' },
        });
        window.clearTimeout(timeout);

        if (!response.ok) {
          throw new Error(`Audius returned HTTP ${response.status}`);
        }

        const json = await response.json();
        const items = Array.isArray(json.data) ? json.data : [];

        return items
          .map((track: Record<string, unknown>) => {
            const user = (track.user as Record<string, unknown>) || {};
            const artwork = (track.artwork as Record<string, string>) || {};
            const trackId = String(track.id || '');
            const artistId = String(user.id || '');
            const artworkUrl =
              artwork['480x480'] ||
              artwork['_480x480'] ||
              artwork['150x150'] ||
              artwork['_150x150'] ||
              '';

            return {
              id: trackId,
              title: String(track.title || 'Untitled Track'),
              artist: String(user.name || user.handle || 'Audius Artist'),
              artistId,
              album: String(track.album_backlink || '').trim() || 'Audius',
              artworkUrl,
              streamUrl: `${base}/tracks/${trackId}/stream?app_name=DJDesktop`,
              genre: String(track.genre || ''),
              duration: (Number(track.duration) || 0) * 1000,
            } satisfies AudiusTrack;
          })
          .filter((track: AudiusTrack) => Boolean(track.id));
      } catch (error) {
        lastError = error;
        this.activeAudiusNode += 1;
      }
    }

    throw new Error(
      lastError instanceof Error
        ? lastError.message
        : 'Unable to connect to Audius.'
    );
  }
}

export const onlineMusicService = new OnlineMusicService();

export function getOnlineDownloadEndpoint(item: {
  id: string;
  uri: string;
  onlineSource?: 'ALBUMATY' | 'AUDIUS' | 'OLDIES' | 'QURAN';
  sourceUrl?: string;
  downloadUri?: string;
  title?: string;
}): string | null {
  if (item.downloadUri) return item.downloadUri;
  const sourceUrl = item.sourceUrl || '';
  const titleName = encodeURIComponent(item.title || 'song');

  if (item.onlineSource === 'ALBUMATY' && sourceUrl) {
    return `/api/albumaty/download?url=${encodeURIComponent(sourceUrl)}&name=${titleName}.mp3`;
  }
  if (item.onlineSource === 'OLDIES' && sourceUrl) {
    return `/api/oldies/download?url=${encodeURIComponent(sourceUrl)}&name=${titleName}.mp3`;
  }
  if (item.onlineSource === 'QURAN' && sourceUrl) {
    return `/api/quran/download?url=${encodeURIComponent(sourceUrl)}&name=${titleName}.mp3`;
  }
  if (item.onlineSource === 'AUDIUS' && sourceUrl) {
    return `/api/online/download?url=${encodeURIComponent(sourceUrl)}&name=${titleName}.mp3`;
  }

  // Handle stream API URIs
  if (item.uri) {
    if (item.uri.startsWith('/api/albumaty/stream?url=')) {
      const parsedUrl = item.uri.slice('/api/albumaty/stream?url='.length);
      return `/api/albumaty/download?url=${parsedUrl}&name=${titleName}.mp3`;
    }
    if (item.uri.startsWith('/api/oldies/stream?url=')) {
      const parsedUrl = item.uri.slice('/api/oldies/stream?url='.length);
      return `/api/oldies/download?url=${parsedUrl}&name=${titleName}.mp3`;
    }
    if (item.uri.startsWith('/api/quran/stream?url=')) {
      const parsedUrl = item.uri.slice('/api/quran/stream?url='.length);
      return `/api/quran/download?url=${parsedUrl}&name=${titleName}.mp3`;
    }
    if (item.uri.startsWith('/api/online/stream?url=')) {
      const parsedUrl = item.uri.slice('/api/online/stream?url='.length);
      return `/api/online/download?url=${parsedUrl}&name=${titleName}.mp3`;
    }
  }

  const legacyAlbumatyUrl = item.id.startsWith('albumaty_')
    ? decodeURIComponent(item.id.slice('albumaty_'.length))
    : item.id.startsWith('oldies_')
    ? decodeURIComponent(item.id.slice('oldies_'.length))
    : item.id;
  if (/^https?:\/\/(?:www\.)?albumaty\.com\/song\//i.test(legacyAlbumatyUrl)) {
    return `/api/albumaty/download?url=${encodeURIComponent(legacyAlbumatyUrl)}&name=${titleName}.mp3`;
  }

  const legacyAudiusUrl = sourceUrl || item.uri;
  try {
    const parsed = new URL(legacyAudiusUrl, window.location.origin);
    if (/^(?:[^.]+\.)*audius\.co$/i.test(parsed.hostname) && parsed.pathname.includes('/tracks/')) {
      return `/api/online/download?url=${encodeURIComponent(legacyAudiusUrl)}&name=${titleName}.mp3`;
    }
    if (/^(?:[^.]+\.)*mp3quran\.net$/i.test(parsed.hostname)) {
      return `/api/quran/download?url=${encodeURIComponent(legacyAudiusUrl)}&name=${titleName}.mp3`;
    }
  } catch {
    // Ignore malformed legacy URLs.
  }

  if (item.onlineSource) {
    return `/api/albumaty/download?url=${encodeURIComponent(sourceUrl || item.uri)}&name=${titleName}.mp3`;
  }

  return null;
}

export function downloadOnlineItem(item: Parameters<typeof getOnlineDownloadEndpoint>[0]): boolean {
  const url = getOnlineDownloadEndpoint(item);
  if (!url) return false;
  const fileName = `${(item.title || 'song').replace(/[\\/:*?"<>|\r\n]/g, '_')}.mp3`;
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  anchor.style.display = 'none';
  document.body.appendChild(anchor);
  anchor.click();
  setTimeout(() => anchor.remove(), 2000);
  return true;
}

export async function downloadOnlineItemAsync(
  item: Parameters<typeof getOnlineDownloadEndpoint>[0],
  onStatus?: (msg: string) => void
): Promise<void> {
  const url = getOnlineDownloadEndpoint(item);
  if (!url) throw new Error('No valid download endpoint found for this track.');
  onStatus?.('Downloading audio stream...');
  const response = await fetch(url, { headers: { Accept: 'audio/*, application/octet-stream, */*' } });
  if (!response.ok) {
    let errMsg = `Download failed with HTTP ${response.status}`;
    try {
      const json = await response.json();
      if (json && (json.error || json.message)) errMsg = json.error || json.message;
    } catch {
      // not JSON
    }
    throw new Error(errMsg);
  }
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    const json = await response.json().catch(() => null);
    throw new Error(json?.error || json?.message || 'Server returned an error instead of audio.');
  }
  const blob = await response.blob();
  if (blob.size === 0) throw new Error('Downloaded audio file was empty.');
  const blobUrl = URL.createObjectURL(blob);
  const fileName = `${(item.title || 'song').replace(/[\\/:*?"<>|\r\n]/g, '_')}.mp3`;
  const anchor = document.createElement('a');
  anchor.href = blobUrl;
  anchor.download = fileName;
  anchor.style.display = 'none';
  document.body.appendChild(anchor);
  anchor.click();
  setTimeout(() => {
    anchor.remove();
    URL.revokeObjectURL(blobUrl);
  }, 15000);
}

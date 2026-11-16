import { AudioItem, Playlist, CustomPreset, RadioStation } from '../types';

const DB_NAME = 'dj_desktop_db';
const DB_VERSION = 3;

class StorageDB {
  private dbPromise: Promise<IDBDatabase>;

  constructor() {
    this.dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;

        if (!db.objectStoreNames.contains('songs')) {
          const songsStore = db.createObjectStore('songs', { keyPath: 'id' });
          songsStore.createIndex('title', 'title', { unique: false });
          songsStore.createIndex('artist', 'artist', { unique: false });
          songsStore.createIndex('addedDate', 'addedDate', { unique: false });
        }

        if (!db.objectStoreNames.contains('playlists')) {
          db.createObjectStore('playlists', { keyPath: 'id' });
        }

        if (!db.objectStoreNames.contains('playlist_songs')) {
          const psStore = db.createObjectStore('playlist_songs', { keyPath: 'id', autoIncrement: true });
          psStore.createIndex('playlistId', 'playlistId', { unique: false });
          psStore.createIndex('songId', 'songId', { unique: false });
        }

        if (!db.objectStoreNames.contains('eq_presets')) {
          db.createObjectStore('eq_presets', { keyPath: 'id' });
        }

        if (!db.objectStoreNames.contains('radio_favorites')) {
          db.createObjectStore('radio_favorites', { keyPath: 'id' });
        }

        if (!db.objectStoreNames.contains('settings')) {
          db.createObjectStore('settings', { keyPath: 'key' });
        }
      };

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  // Recreate temporary browser URLs from persistent file bytes whenever songs are read.
  private hydrateSong(song: AudioItem): AudioItem {
    if (song.audioBlob instanceof Blob) {
      return { ...song, uri: URL.createObjectURL(song.audioBlob) };
    }
    return song;
  }

  // --- SONGS ---
  async getAllSongs(): Promise<AudioItem[]> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('songs', 'readonly');
      const store = tx.objectStore('songs');
      const req = store.getAll();
      req.onsuccess = () => resolve((req.result || []).map((song: AudioItem) => this.hydrateSong(song)));
      req.onerror = () => reject(req.error);
    });
  }

  async addSong(song: AudioItem): Promise<void> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('songs', 'readwrite');
      const store = tx.objectStore('songs');
      const req = store.put(song);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  async addSongs(songs: AudioItem[]): Promise<void> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('songs', 'readwrite');
      const store = tx.objectStore('songs');
      for (const s of songs) {
        store.put(s);
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async deleteSong(id: string): Promise<void> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('songs', 'readwrite');
      const store = tx.objectStore('songs');
      const req = store.delete(id);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  async clearAllSongs(): Promise<void> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('songs', 'readwrite');
      const store = tx.objectStore('songs');
      const req = store.clear();
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  async toggleFavorite(id: string): Promise<boolean> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('songs', 'readwrite');
      const store = tx.objectStore('songs');
      const getReq = store.get(id);
      getReq.onsuccess = () => {
        const song: AudioItem = getReq.result;
        if (!song) {
          resolve(false);
          return;
        }
        song.isFavorite = !song.isFavorite;
        store.put(song);
        resolve(song.isFavorite);
      };
      getReq.onerror = () => reject(getReq.error);
    });
  }

  // --- PLAYLISTS ---
  async getPlaylists(): Promise<Playlist[]> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('playlists', 'readonly');
      const store = tx.objectStore('playlists');
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  }

  async createPlaylist(name: string): Promise<Playlist> {
    const db = await this.dbPromise;
    const playlist: Playlist = {
      id: 'pl_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
      name: name.trim(),
      createdAt: Date.now(),
      songCount: 0,
    };
    return new Promise((resolve, reject) => {
      const tx = db.transaction('playlists', 'readwrite');
      const store = tx.objectStore('playlists');
      const req = store.add(playlist);
      req.onsuccess = () => resolve(playlist);
      req.onerror = () => reject(req.error);
    });
  }

  async deletePlaylist(playlistId: string): Promise<void> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction(['playlists', 'playlist_songs'], 'readwrite');
      tx.objectStore('playlists').delete(playlistId);

      // Clean up junction entries
      const psStore = tx.objectStore('playlist_songs');
      const index = psStore.index('playlistId');
      const req = index.openCursor(IDBKeyRange.only(playlistId));
      req.onsuccess = () => {
        const cursor = req.result;
        if (cursor) {
          cursor.delete();
          cursor.continue();
        }
      };

      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async addSongToPlaylist(playlistId: string, song: AudioItem): Promise<void> {
    const db = await this.dbPromise;
    // ensure song in store
    await this.addSong(song);

    return new Promise((resolve, reject) => {
      const tx = db.transaction(['playlists', 'playlist_songs'], 'readwrite');
      const psStore = tx.objectStore('playlist_songs');
      psStore.add({ playlistId, songId: song.id, addedAt: Date.now() });

      // Update count
      const plStore = tx.objectStore('playlists');
      const plReq = plStore.get(playlistId);
      plReq.onsuccess = () => {
        const pl: Playlist = plReq.result;
        if (pl) {
          pl.songCount = (pl.songCount || 0) + 1;
          plStore.put(pl);
        }
      };

      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async getSongsInPlaylist(playlistId: string): Promise<AudioItem[]> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction(['songs', 'playlist_songs'], 'readonly');
      const psStore = tx.objectStore('playlist_songs');
      const songStore = tx.objectStore('songs');
      const index = psStore.index('playlistId');
      const songIds: string[] = [];

      const cursorReq = index.openCursor(IDBKeyRange.only(playlistId));
      cursorReq.onsuccess = () => {
        const cursor = cursorReq.result;
        if (cursor) {
          songIds.push(cursor.value.songId);
          cursor.continue();
        } else {
          // get all songs
          const results: AudioItem[] = [];
          if (songIds.length === 0) {
            resolve([]);
            return;
          }
          let pending = songIds.length;
          for (const sId of songIds) {
            const req = songStore.get(sId);
            req.onsuccess = () => {
              if (req.result) results.push(this.hydrateSong(req.result));
              pending--;
              if (pending === 0) resolve(results);
            };
            req.onerror = () => {
              pending--;
              if (pending === 0) resolve(results);
            };
          }
        }
      };
      cursorReq.onerror = () => reject(cursorReq.error);
    });
  }

  // --- PRESETS ---
  async getCustomPresets(): Promise<CustomPreset[]> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('eq_presets', 'readonly');
      const store = tx.objectStore('eq_presets');
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  }

  async saveCustomPreset(preset: CustomPreset): Promise<void> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('eq_presets', 'readwrite');
      const store = tx.objectStore('eq_presets');
      const req = store.put(preset);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  async deleteCustomPreset(id: string): Promise<void> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('eq_presets', 'readwrite');
      const store = tx.objectStore('eq_presets');
      const req = store.delete(id);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  // --- RADIO FAVORITES ---
  async getRadioFavorites(): Promise<RadioStation[]> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('radio_favorites', 'readonly');
      const store = tx.objectStore('radio_favorites');
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  }

  async toggleRadioFavorite(station: RadioStation): Promise<boolean> {
    const db = await this.dbPromise;
    return new Promise((resolve, reject) => {
      const tx = db.transaction('radio_favorites', 'readwrite');
      const store = tx.objectStore('radio_favorites');
      const getReq = store.get(station.id);
      getReq.onsuccess = () => {
        if (getReq.result) {
          store.delete(station.id);
          resolve(false);
        } else {
          store.put(station);
          resolve(true);
        }
      };
      getReq.onerror = () => reject(getReq.error);
    });
  }

  // --- GENERIC SETTINGS ---
  async getSetting<T>(key: string, defaultValue: T): Promise<T> {
    try {
      const db = await this.dbPromise;
      return new Promise((resolve) => {
        const tx = db.transaction('settings', 'readonly');
        const store = tx.objectStore('settings');
        const req = store.get(key);
        req.onsuccess = () => {
          resolve(req.result ? (req.result.value as T) : defaultValue);
        };
        req.onerror = () => resolve(defaultValue);
      });
    } catch {
      return defaultValue;
    }
  }

  async setSetting<T>(key: string, value: T): Promise<void> {
    try {
      const db = await this.dbPromise;
      return new Promise((resolve, reject) => {
        const tx = db.transaction('settings', 'readwrite');
        const store = tx.objectStore('settings');
        const req = store.put({ key, value });
        req.onsuccess = () => resolve();
        req.onerror = () => reject(req.error);
      });
    } catch {
      // ignore
    }
  }
}

export const db = new StorageDB();

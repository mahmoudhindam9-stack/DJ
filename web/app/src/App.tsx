import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  TabType,
  AppThemeOption,
  AudioItem,
  Playlist,
  PlaybackMode,
  RadioStation,
  LanguageMode,
} from './types';
import { THEMES } from './utils/theme';
import { db } from './storage/db';
import { mainAudioEngine } from './audio/AudioEngine';
import { deckAEngine, deckBEngine } from './audio/DJDeckEngine';
import { TitleBar } from './components/TitleBar';
import { MiniPlayer } from './components/MiniPlayer';
import { NowPlayingModal } from './components/NowPlayingModal';
import { QueueModal } from './components/QueueModal';
import { LibraryScreen } from './components/screens/LibraryScreen';
import { DJMixerScreen } from './components/screens/DJMixerScreen';
import { EqualizerScreen } from './components/screens/EqualizerScreen';
import { KaraokeScreen } from './components/screens/KaraokeScreen';
import { RadioScreen } from './components/screens/RadioScreen';
import { OnlineMusicScreen } from './components/screens/OnlineMusicScreen';
import { QuranScreen } from './components/screens/QuranScreen';
import { AIMusicStudioScreen } from './components/screens/AIMusicStudioScreen';
import { VideoPlayerScreen } from './components/screens/VideoPlayerScreen';
import { SettingsScreen } from './components/screens/SettingsScreen';
import { cloudSyncService } from './services/CloudSyncService';
import { batchImportAudioFiles } from './utils/fileImporter';
import { DesktopSetupModal } from './components/desktop/DesktopSetupModal';
import { updateService, type UpdateInfo, type UpdateStatus } from './services/UpdateService';
import { WebviewWindow } from '@tauri-apps/api/webviewWindow';
import { isTauri } from '@tauri-apps/api/core';
import { join } from '@tauri-apps/api/path';
import { open as openDialog } from '@tauri-apps/plugin-dialog';
import { exists, writeFile } from '@tauri-apps/plugin-fs';
import { getOnlineDownloadEndpoint } from './services/OnlineMusicService';
import { CONTROL_CHANNEL_NAME, type DesktopControlCommand, type DesktopControlMessage, type ControlPlayerState } from './services/ControlChannel';


type BrowserDownloadWritable = {
  write: (data: Uint8Array) => Promise<void>;
  close: () => Promise<void>;
};
type BrowserDownloadFile = {
  createWritable: () => Promise<BrowserDownloadWritable>;
};
type BrowserDownloadDirectory = {
  getFileHandle: (name: string, options?: { create?: boolean }) => Promise<BrowserDownloadFile>;
};

function safeDownloadName(value: string): string {
  const safe = value
    .normalize('NFKC')
    .replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_')
    .replace(/[. ]+$/g, '')
    .trim()
    .slice(0, 100);
  return safe || 'song';
}

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('LIBRARY');
  const [currentTheme, setCurrentTheme] = useState<AppThemeOption>('DJ_BLUE');
  const [languageMode, setLanguageMode] = useState<LanguageMode>('auto');
  const [isArabic, setIsArabic] = useState<boolean>(() => {
    try {
      const browserLang = (navigator.language || (navigator.languages && navigator.languages[0]) || 'en').toLowerCase();
      return browserLang.startsWith('ar');
    } catch {
      return false;
    }
  });

  // Music State - Clean initial state without default songs
  const [library, setLibrary] = useState<AudioItem[]>([]);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [queue, setQueue] = useState<AudioItem[]>([]);
  const [currentSong, setCurrentSong] = useState<AudioItem | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTimeMs, setCurrentTimeMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [volume, setVolume] = useState(0.85);
  const [crossfadeDurationSeconds, setCrossfadeDurationSeconds] = useState(() => {
    try {
      const saved = Number(window.localStorage.getItem('dj-main-player-crossfade-seconds'));
      return Number.isFinite(saved) && saved >= 0 && saved <= 15 ? Math.round(saved) : 5;
    } catch {
      return 5;
    }
  });
  const [playbackMode, setPlaybackMode] = useState<PlaybackMode>('NORMAL');
  const shuffleBagRef = useRef<string[]>([]);
  const shuffleHistoryRef = useRef<string[]>([]);

  // Radio state
  const [currentRadioStationId, setCurrentRadioStationId] = useState<string | null>(null);

  // Modals
  const [showNowPlaying, setShowNowPlaying] = useState(false);
  const [showQueue, setShowQueue] = useState(false);
  const [downloadProgress, setDownloadProgress] = useState<string | null>(null);
  const [isDownloadingOnlineQueue, setIsDownloadingOnlineQueue] = useState(false);
  const [isSetupModalOpen, setIsSetupModalOpen] = useState(false);
  const [appDataLoaded, setAppDataLoaded] = useState(false);
  const [cloudVaultLoaded, setCloudVaultLoaded] = useState(false);
  const [cloudVaultReconciled, setCloudVaultReconciled] = useState(false);
  const pendingCloudVaultRef = useRef<{
    tracks: AudioItem[];
    playlists: Array<Playlist & { songIds?: string[] }>;
  } | null>(null);
  const cloudUserRef = useRef<string | null>(null);
  const [autoUpdatesEnabled, setAutoUpdatesEnabled] = useState(true);
  const [updateInfo, setUpdateInfo] = useState<UpdateInfo | null>(null);
  const [updateStatus, setUpdateStatus] = useState<UpdateStatus>('idle');
  const [updateMessage, setUpdateMessage] = useState<string | null>(null);
  const [updateBannerDismissed, setUpdateBannerDismissed] = useState(false);

  const themeColors = THEMES[currentTheme] || THEMES.DJ_BLUE;

  const crossfadeTrack: AudioItem | null = (() => {
    if (!currentSong || queue.length === 0 || playbackMode === 'SHUFFLE' || playbackMode === 'REPEAT_ONE') return null;
    const currentIndex = queue.findIndex((track) => track.id === currentSong.id);
    if (currentIndex < 0) return null;
    if (currentIndex < queue.length - 1) return queue[currentIndex + 1];
    if (playbackMode === 'REPEAT_ALL' && queue.length > 1) return queue[0];
    return null;
  })();

  useEffect(() => {
    mainAudioEngine.setCrossfadeDuration(crossfadeDurationSeconds);
  }, [crossfadeDurationSeconds]);

  useEffect(() => {
    try {
      window.localStorage.setItem('dj-main-player-crossfade-seconds', String(crossfadeDurationSeconds));
    } catch {
      // Local storage can be unavailable in restricted browser contexts.
    }
  }, [crossfadeDurationSeconds]);

  useEffect(() => {
    mainAudioEngine.loadCrossfadeTrack(crossfadeDurationSeconds > 0 ? crossfadeTrack : null);
  }, [currentSong?.id, crossfadeTrack?.id, crossfadeTrack?.uri, crossfadeDurationSeconds]);

  // Load persistence (saved songs, playlists, settings)
  useEffect(() => {
    async function loadData() {
      try {
        const savedSongs = await db.getAllSongs();
        const cleanSongs: AudioItem[] = [];

        // Purge any legacy demo tracks from storage
        if (savedSongs && savedSongs.length > 0) {
          for (const s of savedSongs) {
            if (s.id.startsWith('demo_')) {
              await db.deleteSong(s.id);
            } else {
              cleanSongs.push(s);
            }
          }
        }

        setLibrary(cleanSongs);
        setQueue(cleanSongs);
        if (cleanSongs.length > 0) {
          setCurrentSong(cleanSongs[0]);
        }

        const savedPls = await db.getPlaylists();
        setPlaylists(savedPls);

        const savedTheme = await db.getSetting<AppThemeOption>('app_theme', 'DJ_BLUE');
        setCurrentTheme(savedTheme);

        const savedLangMode = await db.getSetting<LanguageMode>('language_mode', 'auto');
        setLanguageMode(savedLangMode);
        if (savedLangMode === 'ar') {
          setIsArabic(true);
        } else if (savedLangMode === 'en') {
          setIsArabic(false);
        } else {
          const browserLang = (navigator.language || (navigator.languages && navigator.languages[0]) || 'en').toLowerCase();
          const detectedAr = browserLang.startsWith('ar');
          setIsArabic(detectedAr);
          await db.setSetting('is_arabic', detectedAr);
        }
        setAutoUpdatesEnabled(await db.getSetting<boolean>('auto_updates_enabled', true));

        // Restore custom equalizer state
        try {
          const savedEq = await db.getSetting<{
            enabled?: boolean;
            preset?: string;
            bands?: number[];
            bassBoost?: number;
            trebleBoost?: number;
            preampDb?: number;
          } | null>('active_eq_state', null);
          if (savedEq) {
            if (typeof savedEq.enabled === 'boolean') mainAudioEngine.setEqEnabled(savedEq.enabled);
            if (Array.isArray(savedEq.bands) && savedEq.bands.length > 0) mainAudioEngine.applyCustomBands(savedEq.bands);
            if (typeof savedEq.bassBoost === 'number') mainAudioEngine.setBassBoostLevel(savedEq.bassBoost);
            if (typeof savedEq.trebleBoost === 'number') mainAudioEngine.setTrebleBoostLevel(savedEq.trebleBoost);
            if (typeof savedEq.preampDb === 'number') mainAudioEngine.setPreampDb(savedEq.preampDb);
          }
        } catch (eqErr) {
          console.warn('Could not restore equalizer settings:', eqErr);
        }
      } catch (e) {
        console.warn('Storage initial load notice:', e);
      } finally {
        setAppDataLoaded(true);
      }
    }
    loadData();
  }, []);

  // Update HTML direction when language changes
  useEffect(() => {
    document.documentElement.dir = isArabic ? 'rtl' : 'ltr';
    document.documentElement.lang = isArabic ? 'ar' : 'en';
  }, [isArabic]);

  // Initialize Neon Cloud Sync & Single Active Audio Output Arbitration
  useEffect(() => {
    cloudSyncService.init({
      onRemoteAudioClaimed: (playback) => {
        if (playback.activeDevicePlatform !== 'web' && playback.isPlaying) {
          mainAudioEngine.pause();
          deckAEngine.pause();
          deckBEngine.pause();
          setIsPlaying(false);
        }
      },
      onUserChanged: async (user) => {
        const nextId = user?.userId || null;
        if (cloudUserRef.current !== nextId) {
          cloudUserRef.current = nextId;
          setCloudVaultLoaded(false);
          setCloudVaultReconciled(false);
          pendingCloudVaultRef.current = null;
          await db.setUserScope(nextId);
          const [scopedTracks, scopedPlaylists] = await Promise.all([
            db.getAllSongs(),
            db.getPlaylists(),
          ]);
          setLibrary(scopedTracks);
          setPlaylists(scopedPlaylists);
        }
      },
      onVaultLoaded: (vault) => {
        pendingCloudVaultRef.current = { tracks: vault.tracks, playlists: vault.playlists };
        setCloudVaultLoaded(true);
        setCloudVaultReconciled(false);
        setPlaylists(vault.playlists.map((playlist) => ({
          ...playlist,
          songCount: playlist.songCount ?? playlist.songIds?.length ?? 0,
        })));
        if (vault.themeName && Object.prototype.hasOwnProperty.call(THEMES, vault.themeName)) {
          setCurrentTheme(vault.themeName as AppThemeOption);
        }
        if (vault.languageMode === 'ar' || vault.languageMode === 'en' || vault.languageMode === 'auto') {
          setLanguageMode(vault.languageMode);
          if (vault.languageMode !== 'auto') setIsArabic(vault.languageMode === 'ar');
        }
      },
    });
  }, []);


  // Restore and synchronize the signed-in user's vault through Neon after the first remote read.
  useEffect(() => {
    if (!appDataLoaded || !cloudVaultLoaded || !cloudVaultReconciled || !cloudSyncService.getUser()) return;
    let cancelled = false;
    const syncVault = async () => {
      try {
        const playlistsWithSongs = await Promise.all(playlists.map(async (playlist) => ({
          ...playlist,
          songIds: (await db.getSongsInPlaylist(playlist.id)).map((song) => song.id),
        })));
        if (cancelled) return;
        const favoriteIds = library.filter((track) => track.isFavorite).map((track) => track.id);
        await cloudSyncService.pushUserVaultState({
          tracks: library.map(({ audioBlob, ...track }) => ({
            ...track,
            uri: audioBlob ? '' : track.uri,
          })),
          playlists: playlistsWithSongs,
          favoriteIds,
          languageMode,
          themeName: currentTheme,
        });
      } catch (error) {
        console.warn('Neon vault sync failed:', error);
      }
    };
    void syncVault();
    return () => { cancelled = true; };
  }, [appDataLoaded, cloudVaultLoaded, cloudVaultReconciled, library, playlists, languageMode, currentTheme]);
  // Reconcile Neon metadata with IndexedDB without discarding local audio blobs.
  useEffect(() => {
    if (!appDataLoaded || !cloudVaultLoaded || cloudVaultReconciled) return;
    let cancelled = false;
    const reconcileVault = async () => {
      try {
        const pending = pendingCloudVaultRef.current;
        if (!pending) return;
        const localTracks = await db.getAllSongs();
        const merged = new Map<string, AudioItem>();
        for (const track of pending.tracks) merged.set(track.id, track as AudioItem);
        for (const track of localTracks) merged.set(track.id, { ...merged.get(track.id), ...track });
        await db.addSongs(Array.from(merged.values()));
        await db.syncCloudPlaylists(pending.playlists);
        if (cancelled) return;
        setLibrary(Array.from(merged.values()));
        setCloudVaultReconciled(true);
      } catch (error) {
        console.warn('Could not reconcile the Neon vault with local storage:', error);
      }
    };
    void reconcileVault();
    return () => { cancelled = true; };
  }, [appDataLoaded, cloudVaultLoaded, cloudVaultReconciled]);

  // Handle Audio Engine subscriptions
  useEffect(() => {
    const unsubTime = mainAudioEngine.onTimeUpdate((cur, dur) => {
      setCurrentTimeMs(cur);
      setDurationMs(dur);
    });

    const unsubState = mainAudioEngine.onStateChange((playing) => {
      setIsPlaying(playing);
    });

    const unsubEnd = mainAudioEngine.onEnded(() => {
      handleTrackEnded();
    });
    const unsubTransition = mainAudioEngine.onTrackTransition((track) => {
      setCurrentSong(track);
      setCurrentTimeMs(mainAudioEngine.currentTimeMs);
      setDurationMs(mainAudioEngine.durationMs);
      setCurrentRadioStationId(null);
    });

    return () => {
      unsubTime();
      unsubState();
      unsubEnd();
      unsubTransition();
    };
  }, [queue, currentSong, playbackMode]);

  // Desktop Global Keyboard Shortcuts
  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable
      ) {
        return;
      }

      if (e.code === 'Space' && activeTab !== 'DJ_MIXER') {
        e.preventDefault();
        handlePlayPause();
      } else if (e.code === 'ArrowLeft' && (e.ctrlKey || e.altKey)) {
        e.preventDefault();
        handlePrev();
      } else if (e.code === 'ArrowRight' && (e.ctrlKey || e.altKey)) {
        e.preventDefault();
        handleNext();
      } else if (e.code === 'ArrowUp' && (e.ctrlKey || e.altKey)) {
        e.preventDefault();
        handleVolumeChange(Math.min(1, volume + 0.05));
      } else if (e.code === 'ArrowDown' && (e.ctrlKey || e.altKey)) {
        e.preventDefault();
        handleVolumeChange(Math.max(0, volume - 0.05));
      } else if (e.key === '1' && e.altKey) {
        setActiveTab('LIBRARY');
      } else if (e.key === '2' && e.altKey) {
        setActiveTab('EQUALIZER');
      } else if (e.key === '3' && e.altKey) {
        setActiveTab('ONLINE_MUSIC');
      } else if (e.key === '4' && e.altKey) {
        setActiveTab('RADIO');
      } else if (e.key === '5' && e.altKey) {
        setActiveTab('QURAN');
      } else if (e.key === '6' && e.altKey) {
        setActiveTab('DJ_MIXER');
      } else if (e.key === '7' && e.altKey) {
        setActiveTab('KARAOKE');
      }
    };

    window.addEventListener('keydown', handleGlobalKeyDown);
    return () => window.removeEventListener('keydown', handleGlobalKeyDown);
  }, [activeTab, isPlaying, currentSong, volume, queue]);

  // Pick the next random track without repeating until the current queue cycle is exhausted.
  const pickNextShuffleTrack = useCallback((tracks: AudioItem[], currentId?: string): AudioItem | null => {
    if (tracks.length === 0) return null;
    const candidates = tracks.filter((track) => track.id !== currentId);
    if (candidates.length === 0) return tracks[0] || null;

    const candidateIds = new Set(candidates.map((track) => track.id));
    let bag = shuffleBagRef.current.filter((id) => candidateIds.has(id));
    if (bag.length === 0) {
      bag = candidates.map((track) => track.id);
      for (let index = bag.length - 1; index > 0; index -= 1) {
        const randomIndex = Math.floor(Math.random() * (index + 1));
        [bag[index], bag[randomIndex]] = [bag[randomIndex], bag[index]];
      }
    }

    const nextId = bag.shift();
    shuffleBagRef.current = bag;
    return candidates.find((track) => track.id === nextId) || candidates[0];
  }, []);

  // Track end logic
  const handleTrackEnded = useCallback(() => {
    if (playbackMode === 'REPEAT_ONE' && currentSong) {
      mainAudioEngine.seekTo(0);
      void mainAudioEngine.play();
      return;
    }

    if (queue.length === 0) return;

    if (playbackMode === 'SHUFFLE') {
      const randomTrack = pickNextShuffleTrack(queue, currentSong?.id);
      if (randomTrack) handlePlaySong(randomTrack);
      return;
    }

    const currentIndex = queue.findIndex((song) => song.id === currentSong?.id);
    if (currentIndex >= 0 && currentIndex < queue.length - 1) {
      handlePlaySong(queue[currentIndex + 1]);
    } else if (playbackMode === 'REPEAT_ALL') {
      handlePlaySong(queue[0]);
    } else {
      setIsPlaying(false);
    }
  }, [queue, currentSong, playbackMode, pickNextShuffleTrack]);

  // Play Song
  const handlePlaySong = (song: AudioItem, customQueue?: AudioItem[], recordShuffleHistory = true) => {
    if (customQueue && customQueue.length > 0) {
      setQueue(customQueue);
      const validIds = new Set(customQueue.map((track) => track.id));
      shuffleBagRef.current = shuffleBagRef.current.filter((id) => validIds.has(id));
      shuffleHistoryRef.current = shuffleHistoryRef.current.filter((id) => validIds.has(id));
    } else if (!song.id.startsWith('radio_')) {
      // Directly played online songs join the queue so the next item can preload/crossfade.
      setQueue((current) => current.some((track) => track.id === song.id) ? current : [song, ...current]);
    }

    if (
      recordShuffleHistory &&
      playbackMode === 'SHUFFLE' &&
      currentSong &&
      currentSong.id !== song.id &&
      (!customQueue || customQueue.some((track) => track.id === currentSong.id))
    ) {
      const history = shuffleHistoryRef.current;
      if (history[history.length - 1] !== currentSong.id) history.push(currentSong.id);
      if (history.length > 300) history.splice(0, history.length - 300);
    }

    setCurrentSong(song);
    setCurrentTimeMs(0);
    setDurationMs(song.duration || 0);
    setCurrentRadioStationId(null);
    void cloudSyncService.claimWebAudioOutput(song.id, song.title, 0);
    void mainAudioEngine.loadTrack(song).then(() => mainAudioEngine.play());
  };

  // Play / Pause Toggle
  const handlePlayPause = () => {
    if (!currentSong) {
      if (queue.length > 0) {
        handlePlaySong(queue[0]);
      } else if (library.length > 0) {
        handlePlaySong(library[0]);
      }
      return;
    }

    if (isPlaying) {
      mainAudioEngine.pause();
      void cloudSyncService.pauseWebAudioOutput(currentTimeMs);
    } else {
      void cloudSyncService.claimWebAudioOutput(currentSong.id, currentSong.title, currentTimeMs);
      mainAudioEngine.play();
    }
  };

  const handleNext = () => {
    if (queue.length === 0) return;
    if (playbackMode === 'SHUFFLE') {
      const randomTrack = pickNextShuffleTrack(queue, currentSong?.id);
      if (randomTrack) handlePlaySong(randomTrack);
      return;
    }
    const currentIndex = queue.findIndex((song) => song.id === currentSong?.id);
    const nextIndex = (currentIndex + 1) % queue.length;
    handlePlaySong(queue[nextIndex]);
  };

  const handlePrev = () => {
    if (queue.length === 0) return;
    if (currentTimeMs > 3000) {
      mainAudioEngine.seekTo(0);
      return;
    }
    if (playbackMode === 'SHUFFLE') {
      const history = shuffleHistoryRef.current;
      while (history.length > 0) {
        const previousId = history.pop();
        const previousTrack = queue.find((song) => song.id === previousId);
        if (previousTrack) {
          if (currentSong && currentSong.id !== previousTrack.id) {
            shuffleBagRef.current = [
              currentSong.id,
              ...shuffleBagRef.current.filter((id) => id !== currentSong.id),
            ];
          }
          handlePlaySong(previousTrack, undefined, false);
          return;
        }
      }
      // No shuffle history yet: start from a different random item.
      const randomTrack = pickNextShuffleTrack(queue, currentSong?.id);
      if (randomTrack) handlePlaySong(randomTrack, undefined, false);
      return;
    }
    const currentIndex = queue.findIndex((song) => song.id === currentSong?.id);
    const prevIndex = (currentIndex - 1 + queue.length) % queue.length;
    handlePlaySong(queue[prevIndex]);
  };

  const handleSeek = (positionMs: number) => {
    const engineDuration = mainAudioEngine.durationMs;
    const safeDuration = engineDuration > 0 ? engineDuration : durationMs;
    const safePosition = Math.max(0, safeDuration > 0 ? Math.min(safeDuration, positionMs) : positionMs);
    mainAudioEngine.seekTo(safePosition);
    setCurrentTimeMs(safePosition);
  };

  const handleSeekBy = (deltaMs: number) => {
    const engineTime = mainAudioEngine.currentTimeMs;
    const current = Number.isFinite(engineTime) ? engineTime : currentTimeMs;
    const engineDuration = mainAudioEngine.durationMs;
    const safeDuration = engineDuration > 0 ? engineDuration : durationMs;
    const target = Math.max(0, current + deltaMs);
    handleSeek(safeDuration > 0 ? Math.min(safeDuration, target) : target);
  };

  const handleVolumeChange = (v: number) => {
    setVolume(v);
    mainAudioEngine.setVolume(v);
  };

  const handleCrossfadeDurationChange = (seconds: number) => {
    setCrossfadeDurationSeconds(Math.max(0, Math.min(15, Math.round(Number.isFinite(seconds) ? seconds : 0))));
  };

  const handleTogglePlaybackMode = () => {
    const modes: PlaybackMode[] = ['NORMAL', 'REPEAT_ONE', 'REPEAT_ALL', 'SHUFFLE'];
    const nextIdx = (modes.indexOf(playbackMode) + 1) % modes.length;
    if (modes[nextIdx] === 'SHUFFLE') {
      shuffleBagRef.current = [];
      shuffleHistoryRef.current = [];
    }
    setPlaybackMode(modes[nextIdx]);
  };

  const handleToggleFavorite = async (song: AudioItem) => {
    const updatedFav = await db.toggleFavorite(song.id);
    song.isFavorite = updatedFav;
    setLibrary([...library]);
    if (currentSong?.id === song.id) {
      setCurrentSong({ ...song, isFavorite: updatedFav });
    }
  };

  const handleDeleteSong = async (id: string) => {
    await db.deleteSong(id);
    const updated = library.filter((s) => s.id !== id);
    setLibrary(updated);
    setQueue(queue.filter((s) => s.id !== id));
    if (currentSong?.id === id) {
      mainAudioEngine.pause();
      setCurrentSong(updated[0] || null);
    }
  };

  const handleImportFiles = async (files: FileList | File[]): Promise<number> => {
    try {
      const newItems = await batchImportAudioFiles(files);
      if (newItems.length === 0) return 0;

      await db.addSongs(newItems);
      setLibrary((current) => [...newItems, ...current]);
      setQueue((current) => [...newItems, ...current]);
      if (!currentSong) handlePlaySong(newItems[0]);
      return newItems.length;
    } catch (err) {
      console.warn('Importing audio files failed:', err);
      return -1;
    }
  };

  const handleClearLibrary = async () => {
    await db.clearAllSongs();
    setLibrary([]);
    setQueue([]);
    setCurrentSong(null);
    mainAudioEngine.pause();
  };

  // Playlists
  const handleCreatePlaylist = async (name: string): Promise<Playlist> => {
    const created = await db.createPlaylist(name);
    setPlaylists((current) => [...current.filter((playlist) => playlist.id !== created.id), created]);
    return created;
  };

  const handleDeletePlaylist = async (id: string) => {
    await db.deletePlaylist(id);
    setPlaylists(playlists.filter((p) => p.id !== id));
  };

  const handleAddSongToPlaylist = async (playlistId: string, song: AudioItem) => {
    await db.addSongToPlaylist(playlistId, song);
    const refreshed = await db.getPlaylists();
    setPlaylists(refreshed);
  };

  const handleEnqueueSong = (song: AudioItem) => {
    setQueue((current) => [...current, song]);
  };

  const handleDownloadOnlineTracks = async (tracks: AudioItem[]) => {
    // Only process online tracks
    const downloadableTracks = tracks.filter((track) => Boolean(track.onlineSource || getOnlineDownloadEndpoint(track)));
    if (!downloadableTracks.length || isDownloadingOnlineQueue) return;

    setIsDownloadingOnlineQueue(true);
    setDownloadProgress(isArabic ? 'جاري تجهيز تنزيل أغاني الأونلاين…' : 'Preparing online downloads…');
    try {
      let folderPath: string | null = null;
      let browserFolder: BrowserDownloadDirectory | null = null;
      if (isTauri()) {
        try {
          const selected = await openDialog({
            directory: true,
            multiple: false,
            title: isArabic ? 'اختر مجلد حفظ الأغاني' : 'Choose a folder for downloaded songs',
          });
          if (typeof selected === 'string' && selected) {
            folderPath = selected;
          }
        } catch {
          // Continue to fallback if dialog fails
        }
      }

      let completed = 0;
      const failures: string[] = [];
      const usedNames = new Set<string>();
      for (let index = 0; index < downloadableTracks.length; index += 1) {
        const track = downloadableTracks[index];
        const endpoint = getOnlineDownloadEndpoint(track);
        if (!endpoint) continue;
        setDownloadProgress(isArabic
          ? `جاري تنزيل (${index + 1}/${downloadableTracks.length}): ${track.title}…`
          : `Downloading (${index + 1}/${downloadableTracks.length}): ${track.title}…`);
        try {
          const response = await fetch(endpoint, { headers: { Accept: 'audio/*, application/octet-stream, */*' } });
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          const contentType = response.headers.get('content-type') || '';
          if (contentType.includes('application/json')) {
            const payload = await response.json().catch(() => null) as { error?: string; message?: string } | null;
            throw new Error(payload?.error || payload?.message || 'The service did not return an audio file.');
          }
          const bytes = new Uint8Array(await response.arrayBuffer());
          if (!bytes.byteLength) throw new Error('The downloaded file is empty.');
          const baseName = `${safeDownloadName(track.title)} - ${safeDownloadName(track.artist)}.mp3`;
          const dot = baseName.lastIndexOf('.');
          const stem = baseName.slice(0, dot);
          const extension = baseName.slice(dot);
          let name = baseName;
          let suffix = 2;
          while (usedNames.has(name)) name = `${stem} (${suffix++})${extension}`;

          if (folderPath) {
            let target = await join(folderPath, name);
            while (await exists(target)) {
              name = `${stem} (${suffix++})${extension}`;
              target = await join(folderPath, name);
            }
            await writeFile(target, bytes);
          } else {
            // Direct browser download via Blob URL
            const blob = new Blob([bytes], { type: contentType.startsWith('audio/') ? contentType : 'audio/mpeg' });
            const blobUrl = URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = blobUrl;
            anchor.download = name;
            anchor.style.display = 'none';
            document.body.appendChild(anchor);
            anchor.click();
            setTimeout(() => {
              anchor.remove();
              URL.revokeObjectURL(blobUrl);
            }, 15000);
            await new Promise((resolve) => setTimeout(resolve, 800));
          }
          usedNames.add(name);
          completed += 1;
        } catch (error) {
          failures.push(`${track.title}: ${error instanceof Error ? error.message : 'Download failed'}`);
        }
      }
      setDownloadProgress(failures.length === 0
        ? (isArabic ? `اكتمل تنزيل أغاني الأونلاين بنجاح: ${completed} أغنية.` : `Download complete: ${completed} online song(s) saved.`)
        : (isArabic
          ? `انتهى التنزيل: نجح ${completed} وفشل ${failures.length}. أول خطأ: ${failures[0]}`
          : `Download finished: ${completed} succeeded, ${failures.length} failed. First error: ${failures[0]}`));
    } catch (error) {
      setDownloadProgress(error instanceof Error ? error.message : (isArabic ? 'فشل تنزيل الأغاني.' : 'Could not download the songs.'));
    } finally {
      setIsDownloadingOnlineQueue(false);
    }
  };

  // DJ Deck Routing
  const handleSendToDeckA = (song: AudioItem) => {
    deckAEngine.loadTrack(song);
    setActiveTab('DJ_MIXER');
  };

  const handleSendToDeckB = (song: AudioItem) => {
    deckBEngine.loadTrack(song);
    setActiveTab('DJ_MIXER');
  };

  // Radio
  const handlePlayRadioStation = (station: RadioStation) => {
    if (currentRadioStationId === station.id && isPlaying) {
      mainAudioEngine.pause();
      setCurrentRadioStationId(null);
      return;
    }

    const item: AudioItem = {
      id: 'radio_' + station.id,
      title: station.name,
      artist: 'Live Radio FM Direct',
      album: station.tags || 'Broadcasting',
      duration: 0,
      uri: station.streamUrls[0] || '',
      fallbackUris: station.streamUrls.slice(1),
      addedDate: Date.now(),
    };

    setCurrentRadioStationId(station.id);
    handlePlaySong(item);
  };

  // Theme & Language
  const handleThemeChange = async (th: AppThemeOption) => {
    setCurrentTheme(th);
    await db.setSetting('app_theme', th);
  };

  const resolveLanguageMode = (mode: LanguageMode): boolean => {
    if (mode === 'ar') return true;
    if (mode === 'en') return false;
    const browserLang = (navigator.language || (navigator.languages && navigator.languages[0]) || 'en').toLowerCase();
    return browserLang.startsWith('ar');
  };

  const handleLanguageModeChange = async (mode: LanguageMode) => {
    const resolvedAr = resolveLanguageMode(mode);
    setLanguageMode(mode);
    setIsArabic(resolvedAr);
    await db.setSetting('language_mode', mode);
    await db.setSetting('is_arabic', resolvedAr);
  };

  const handleToggleLanguage = async () => {
    const nextMode: LanguageMode = isArabic ? 'en' : 'ar';
    await handleLanguageModeChange(nextMode);
  };

  const handleAutoUpdatesEnabledChange = async (enabled: boolean) => {
    setAutoUpdatesEnabled(enabled);
    await db.setSetting('auto_updates_enabled', enabled);
  };

  const handleCheckForUpdates = async () => {
    setUpdateStatus('checking');
    setUpdateMessage(isArabic ? 'جاري الاتصال بخادم التحديثات...' : 'Checking the update server...');
    try {
      const result = await updateService.checkForUpdates();
      setUpdateInfo(result);
      setUpdateStatus(result.updateAvailable ? 'available' : 'upToDate');
      setUpdateMessage(result.updateAvailable
        ? (isArabic ? `يتوفر الإصدار ${result.latestVersion}.` : `Version ${result.latestVersion} is available.`)
        : (isArabic ? `أنت تستخدم أحدث إصدار (${result.currentVersion}).` : `You are using the latest version (${result.currentVersion}).`));
    } catch (error) {
      setUpdateStatus('error');
      setUpdateMessage(error instanceof Error ? error.message : (isArabic ? 'تعذر فحص التحديثات.' : 'Could not check for updates.'));
    }
  };

  const handleInstallUpdate = async () => {
    if (!updateInfo) return;
    if (!updateInfo.canAutoInstall) {
      window.open(updateInfo.releaseUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    setUpdateStatus('installing');
    setUpdateMessage(updateInfo.downloadedPackageAvailable
      ? (isArabic ? 'ملف الإصدار موجود بالفعل في Downloads؛ سيتم التحقق منه واستخدامه دون تنزيل نسخة مكررة.' : 'The versioned ZIP is already in Downloads; it will be verified and reused without a duplicate download.')
      : (isArabic ? 'سيتم تنزيل ملف التحديث والتحقق منه في Downloads أولًا، ثم تثبيته وإعادة فتح البرنامج.' : 'The update ZIP will first be downloaded and verified in Downloads, then installed before the app reopens.'));
    try {
      await updateService.installUpdate();
    } catch (error) {
      setUpdateStatus('error');
      setUpdateMessage(error instanceof Error ? error.message : (isArabic ? 'تعذر بدء التحديث.' : 'Could not start the update.'));
    }
  };

  useEffect(() => {
    if (!appDataLoaded || !autoUpdatesEnabled) return;
    let disposed = false;
    const check = async () => {
      try {
        const result = await updateService.checkForUpdates();
        if (disposed) return;
        setUpdateInfo(result);
        setUpdateStatus(result.updateAvailable ? 'available' : 'upToDate');
        setUpdateMessage(result.updateAvailable
          ? (isArabic ? `يتوفر تحديث جديد: ${result.latestVersion}.` : `A new update is available: ${result.latestVersion}.`)
          : (isArabic ? 'التطبيق محدث.' : 'The application is up to date.'));
      } catch (error) {
        if (!disposed) {
          setUpdateStatus('error');
          setUpdateMessage(error instanceof Error ? error.message : (isArabic ? 'تعذر فحص التحديثات تلقائيًا.' : 'Automatic update check failed.'));
        }
      }
    };
    void check();
    const timer = window.setInterval(() => { void check(); }, 24 * 60 * 60 * 1000);
    return () => { disposed = true; window.clearInterval(timer); };
  }, [appDataLoaded, autoUpdatesEnabled, isArabic]);

  // Note: Automatic background downloading/installation of updates is disabled by design.
  // When a newer release is detected, a compact notification banner appears at the top of the app,
  // and downloading/installing happens only when the user explicitly clicks the update button.



  // Connect the main player to Windows/browser OS media controls and media keys.
  // Custom buttons inside the Windows taskbar thumbnail itself require a native window host.
  const mediaActionsRef = useRef({
    play: () => {},
    pause: () => {},
    next: () => {},
    previous: () => {},
    seek: (_positionMs: number) => {},
    currentTimeMs: 0,
    durationMs: 0,
  });
  mediaActionsRef.current = {
    play: () => {
      if (isPlaying) return;
      if (currentSong) void mainAudioEngine.play();
      else handlePlayPause();
    },
    pause: () => {
      if (isPlaying) mainAudioEngine.pause();
    },
    next: () => handleNext(),
    previous: () => handlePrev(),
    seek: (positionMs: number) => handleSeek(positionMs),
    currentTimeMs,
    durationMs,
  };

  useEffect(() => {
    const mediaSession = navigator.mediaSession;
    if (!mediaSession) return;
    const handlers: Partial<Record<MediaSessionAction, MediaSessionActionHandler>> = {
      play: () => mediaActionsRef.current.play(),
      pause: () => mediaActionsRef.current.pause(),
      nexttrack: () => mediaActionsRef.current.next(),
      previoustrack: () => mediaActionsRef.current.previous(),
      seekto: (details) => {
        if (typeof details.seekTime === 'number' && Number.isFinite(details.seekTime)) {
          mediaActionsRef.current.seek(details.seekTime * 1000);
        }
      },
      seekbackward: (details) => {
        const offset = Number.isFinite(details.seekOffset) ? details.seekOffset! : 10;
        mediaActionsRef.current.seek(Math.max(0, mediaActionsRef.current.currentTimeMs - offset * 1000));
      },
      seekforward: (details) => {
        const offset = Number.isFinite(details.seekOffset) ? details.seekOffset! : 10;
        const max = mediaActionsRef.current.durationMs || Number.MAX_SAFE_INTEGER;
        mediaActionsRef.current.seek(Math.min(max, mediaActionsRef.current.currentTimeMs + offset * 1000));
      },
      stop: () => {
        mediaActionsRef.current.pause();
        mediaActionsRef.current.seek(0);
      },
    };
    const registered = Object.keys(handlers) as MediaSessionAction[];
    for (const action of registered) {
      try { mediaSession.setActionHandler(action, handlers[action] || null); } catch { /* Platform may not support every action. */ }
    }
    return () => {
      for (const action of registered) {
        try { mediaSession.setActionHandler(action, null); } catch { /* Unsupported action. */ }
      }
    };
  }, []);

  useEffect(() => {
    const mediaSession = navigator.mediaSession;
    if (!mediaSession) return;
    try {
      mediaSession.metadata = currentSong
        ? new MediaMetadata({
            title: currentSong.title || 'DJ Desktop',
            artist: currentSong.artist || 'Unknown artist',
            album: currentSong.album || 'DJ Desktop',
            artwork: currentSong.coverUri
              ? [{ src: currentSong.coverUri, sizes: '512x512', type: 'image/*' }]
              : [],
          })
        : null;
      mediaSession.playbackState = currentSong ? (isPlaying ? 'playing' : 'paused') : 'none';
    } catch {
      // Metadata support varies across browser versions and platforms.
    }
  }, [currentSong?.id, currentSong?.title, currentSong?.artist, currentSong?.album, currentSong?.coverUri, isPlaying]);

  useEffect(() => {
    const mediaSession = navigator.mediaSession;
    if (!mediaSession || durationMs <= 0) return;
    try {
      mediaSession.setPositionState({
        duration: durationMs / 1000,
        playbackRate: 1,
        position: Math.max(0, Math.min(durationMs / 1000, currentTimeMs / 1000)),
      });
    } catch {
      // Position reporting is optional.
    }
  }, [currentTimeMs, durationMs, currentSong?.id]);

  const controlChannelRef = useRef<BroadcastChannel | null>(null);
  const controlSnapshotRef = useRef<ControlPlayerState | null>(null);
  const controlCommandHandlerRef = useRef<(command: DesktopControlCommand) => void>(() => {});

  controlSnapshotRef.current = {
    song: currentSong ? {
      id: currentSong.id,
      title: currentSong.title,
      artist: currentSong.artist,
      album: currentSong.album,
      coverUri: currentSong.coverUri && /^https?:\/\//i.test(currentSong.coverUri) ? currentSong.coverUri : undefined,
    } : null,
    isPlaying,
    currentTimeMs,
    durationMs,
    volume,
    playbackMode,
    isArabic,
    themeColors,
  };

  controlCommandHandlerRef.current = (command) => {
    switch (command.action) {
      case 'toggle':
        handlePlayPause();
        break;
      case 'next':
        handleNext();
        break;
      case 'previous':
        handlePrev();
        break;
      case 'seek':
        if (Number.isFinite(command.ms)) {
          handleSeek(Math.max(0, Math.min(durationMs || command.ms, command.ms)));
        }
        break;
      case 'volume':
        if (Number.isFinite(command.value)) {
          handleVolumeChange(Math.max(0, Math.min(1, command.value)));
        }
        break;
      case 'toggle-playback-mode':
        handleTogglePlaybackMode();
        break;
    }
  };

  const handleOpenControlPanel = async () => {
    try {
      if (isTauri()) {
        const existing = await WebviewWindow.getByLabel('dj-control-panel');
        if (existing) {
          await existing.show();
          await existing.setFocus();
          return;
        }
        const panel = new WebviewWindow('dj-control-panel', {
          url: '/?control=1',
          title: 'DJ Control Center',
          width: 390,
          height: 780,
          minWidth: 340,
          minHeight: 560,
          resizable: true,
          center: true,
          alwaysOnTop: true,
        });
        panel.once('tauri://error', (event) => console.error('Could not create DJ control panel:', event));
      } else {
        const controlWindow = window.open(
          window.location.origin + '/?control=1',
          'dj-control-panel',
          'popup=yes,width=390,height=780,resizable=yes'
        );
        if (!controlWindow) console.warn('The browser blocked the control panel popup.');
      }
    } catch (error) {
      console.error('Could not open DJ control panel:', error);
    }
  };

  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return;
    const channel = new BroadcastChannel(CONTROL_CHANNEL_NAME);
    controlChannelRef.current = channel;
    channel.onmessage = (event: MessageEvent<DesktopControlMessage>) => {
      const message = event.data;
      if (message?.type === 'request-state') {
        channel.postMessage({ type: 'player-state', state: controlSnapshotRef.current } satisfies DesktopControlMessage);
      } else if (message?.type === 'command') {
        controlCommandHandlerRef.current(message.command);
      }
    };
    return () => {
      channel.close();
      controlChannelRef.current = null;
    };
  }, []);

  useEffect(() => {
    controlChannelRef.current?.postMessage({
      type: 'player-state',
      state: controlSnapshotRef.current,
    } satisfies DesktopControlMessage);
  }, [
    currentSong?.id,
    currentSong?.title,
    currentSong?.artist,
    currentSong?.album,
    currentSong?.coverUri,
    isPlaying,
    currentTimeMs,
    durationMs,
    volume,
    playbackMode,
    isArabic,
    themeColors,
  ]);

  return (
    <div
      id="dj-desktop-app"
      className="flex flex-col h-screen w-screen overflow-hidden font-sans select-none"
      style={{
        backgroundColor: themeColors.background,
        color: themeColors.textPrimary,
        '--dj-ui-primary': themeColors.primary,
        '--dj-ui-secondary': themeColors.secondary,
        '--dj-ui-surface': themeColors.surface,
        '--dj-ui-border': themeColors.border,
      } as React.CSSProperties}
    >
      {/* Top Windows Native Desktop Title Bar */}
      <TitleBar
        activeTab={activeTab}
        onTabChange={setActiveTab}
        currentTheme={currentTheme}
        onThemeChange={handleThemeChange}
        isArabic={isArabic}
        languageMode={languageMode}
        onToggleLanguage={handleToggleLanguage}
        onLanguageModeChange={handleLanguageModeChange}
        themeColors={themeColors}
        onOpenSetupModal={() => setIsSetupModalOpen(true)}
        onOpenControlPanel={handleOpenControlPanel}
      />

      {/* Very small compact top update notification banner when a new version is detected */}
      {updateInfo?.updateAvailable && !updateBannerDismissed && (
        <div
          data-testid="top-update-banner"
          className="px-3 py-1 border-b flex items-center justify-between gap-2 text-[11px] font-bold shrink-0"
          style={{
            backgroundColor: `${themeColors.primary}22`,
            borderColor: `${themeColors.primary}55`,
            color: themeColors.textPrimary,
          }}
        >
          <span>
            {isArabic
              ? `🔔 تتوفر نسخة جديدة (${updateInfo.latestVersion}) • يمكنك تنزيلها من زر التحديثات في الإعدادات`
              : `🔔 New version available (${updateInfo.latestVersion}) • Download via Check for Updates in Settings`}
          </span>
          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={() => setActiveTab('SETTINGS')}
              className="px-2 py-0.5 rounded text-[10px] font-extrabold"
              style={{ backgroundColor: themeColors.primary, color: '#ffffff' }}
            >
              {isArabic ? 'صفحة التحديث' : 'Open Updates'}
            </button>
            <button
              type="button"
              onClick={() => setUpdateBannerDismissed(true)}
              className="px-1.5 py-0.5 rounded hover:bg-white/10 text-xs"
              title={isArabic ? 'إغلاق التنبيه' : 'Dismiss'}
            >
              ×
            </button>
          </div>
        </div>
      )}

      {/* Main Screen Content Viewport */}
      <main className="flex-1 flex flex-col min-h-0 overflow-hidden relative">
        {activeTab === 'LIBRARY' && (
          <LibraryScreen
            library={library}
            playlists={playlists}
            currentSong={currentSong}
            isPlaying={isPlaying}
            themeColors={themeColors}
            isArabic={isArabic}
            onPlaySong={handlePlaySong}
            onToggleFavorite={handleToggleFavorite}
            onDeleteSong={handleDeleteSong}
            onClearLibrary={handleClearLibrary}
            onImportFiles={handleImportFiles}
            onNavigateToOnline={() => setActiveTab('ONLINE_MUSIC')}
            onCreatePlaylist={handleCreatePlaylist}
            onDeletePlaylist={handleDeletePlaylist}
            onAddSongToPlaylist={handleAddSongToPlaylist}
            onLoadPlaylistSongs={(playlistId) => db.getSongsInPlaylist(playlistId)}
            onEnqueueSong={handleEnqueueSong}
            onSendToDeckA={handleSendToDeckA}
            onSendToDeckB={handleSendToDeckB}
          />
        )}

        {activeTab === 'DJ_MIXER' && (
          <DJMixerScreen
            library={library}
            themeColors={themeColors}
            isArabic={isArabic}
            onPauseMainPlayer={() => mainAudioEngine.pause()}
            onImportFiles={handleImportFiles}
          />
        )}

        {activeTab === 'EQUALIZER' && (
          <EqualizerScreen themeColors={themeColors} isArabic={isArabic} />
        )}

        {activeTab === 'KARAOKE' && (
          <KaraokeScreen themeColors={themeColors} isArabic={isArabic} />
        )}

        {activeTab === 'RADIO' && (
          <RadioScreen
            themeColors={themeColors}
            isArabic={isArabic}
            onPlayStation={handlePlayRadioStation}
            currentPlayingStationId={currentRadioStationId}
            isPlayingRadio={isPlaying && !!currentRadioStationId}
            onSendToDeckA={handleSendToDeckA}
            onSendToDeckB={handleSendToDeckB}
            onEnqueueSong={handleEnqueueSong}
          />
        )}

        {activeTab === 'ONLINE_MUSIC' && (
          <OnlineMusicScreen
            themeColors={themeColors}
            isArabic={isArabic}
            onPlayOnlineTrack={handlePlaySong}
            onSendToDeckA={handleSendToDeckA}
            onSendToDeckB={handleSendToDeckB}
            onEnqueueTrack={handleEnqueueSong}
            onSaveToLibrary={async (track) => {
              await db.addSong(track);
              setLibrary((current) => [track, ...current.filter((song) => song.id !== track.id)]);
            }}
            playlists={playlists}
            onAddSongToPlaylist={handleAddSongToPlaylist}
            onCreatePlaylist={handleCreatePlaylist}
            onOpenQueue={() => setShowQueue(true)}
          />
        )}

        {activeTab === 'QURAN' && (
          <QuranScreen
            themeColors={themeColors}
            isArabic={isArabic}
            currentPlayingId={currentSong?.id}
            isPlaying={isPlaying}
            playlists={playlists}
            favoriteIds={new Set(library.filter((s) => s.isFavorite).map((s) => s.id))}
            onPlayTrack={handlePlaySong}
            onEnqueueTrack={handleEnqueueSong}
            onSaveToLibrary={async (track) => {
              await db.addSong(track);
              setLibrary((current) => [track, ...current.filter((song) => song.id !== track.id)]);
            }}
            onToggleFavorite={handleToggleFavorite}
            onAddToPlaylist={(playlistId, item) => {
              void handleAddSongToPlaylist(playlistId, item);
            }}
            onCreatePlaylistAndAdd={(name, item) => {
              void (async () => {
                const created = await handleCreatePlaylist(name);
                await handleAddSongToPlaylist(created.id, item);
              })();
            }}
            onSendToDeckA={handleSendToDeckA}
            onSendToDeckB={handleSendToDeckB}
          />
        )}

        {activeTab === 'AI_STUDIO' && (
          <AIMusicStudioScreen
            themeColors={themeColors}
            isArabic={isArabic}
            onPlayGeneratedTrack={handlePlaySong}
            onSendToDeckA={handleSendToDeckA}
            onSendToDeckB={handleSendToDeckB}
            onSaveToLibrary={async (track) => {
              await db.addSong(track);
              setLibrary((current) => [track, ...current.filter((song) => song.id !== track.id)]);
              setQueue((current) => [track, ...current.filter((song) => song.id !== track.id)]);
            }}
          />
        )}

        {activeTab === 'VIDEO' && (
          <VideoPlayerScreen
            isAr={isArabic}
            onPauseAllAudio={() => {
              mainAudioEngine.pause();
              deckAEngine.pause();
              deckBEngine.pause();
              setIsPlaying(false);
            }}
          />
        )}

        {activeTab === 'SETTINGS' && (
          <SettingsScreen
            currentTheme={currentTheme}
            onThemeChange={handleThemeChange}
            isArabic={isArabic}
            languageMode={languageMode}
            onToggleLanguage={handleToggleLanguage}
            onLanguageModeChange={handleLanguageModeChange}
            themeColors={themeColors}
            onOpenSetupModal={() => setIsSetupModalOpen(true)}
            autoUpdatesEnabled={autoUpdatesEnabled}
            onAutoUpdatesEnabledChange={handleAutoUpdatesEnabledChange}
            updateStatus={updateStatus}
            updateInfo={updateInfo}
            updateMessage={updateMessage}
            onCheckForUpdates={handleCheckForUpdates}
            onInstallUpdate={handleInstallUpdate}
          />
        )}
      </main>

      {/* Docked Desktop MiniPlayer (hidden on DJ Studio so the decks and pads have full screen space; compact single-line bar on Equalizer) */}
      {activeTab !== 'DJ_MIXER' && (
        <MiniPlayer
          currentSong={currentSong}
          isPlaying={isPlaying}
          currentTimeMs={currentTimeMs}
          durationMs={durationMs}
          playbackMode={playbackMode}
          volume={volume}
          crossfadeDurationSeconds={crossfadeDurationSeconds}
          themeColors={themeColors}
          isArabic={isArabic}
          compact={activeTab === 'EQUALIZER'}
          onCrossfadeDurationChange={handleCrossfadeDurationChange}
          onPlayPause={handlePlayPause}
          onNext={handleNext}
          onPrev={handlePrev}
          onSeek={handleSeek}
          onSeekBy={handleSeekBy}
          onVolumeChange={handleVolumeChange}
          onTogglePlaybackMode={handleTogglePlaybackMode}
          onOpenQueue={() => setShowQueue(true)}
          onOpenNowPlaying={() => setShowNowPlaying(true)}
          onOpenEqualizer={() => setActiveTab('EQUALIZER')}
        />
      )}

      {/* Fullscreen Now Playing Overlay */}
      {showNowPlaying && (
        <NowPlayingModal
          currentSong={currentSong}
          isPlaying={isPlaying}
          currentTimeMs={currentTimeMs}
          durationMs={durationMs}
          playbackMode={playbackMode}
          volume={volume}
          themeColors={themeColors}
          isArabic={isArabic}
          onClose={() => setShowNowPlaying(false)}
          onPlayPause={handlePlayPause}
          onNext={handleNext}
          onPrev={handlePrev}
          onSeek={handleSeek}
          onVolumeChange={handleVolumeChange}
          onTogglePlaybackMode={handleTogglePlaybackMode}
          onToggleFavorite={handleToggleFavorite}
          onOpenQueue={() => {
            setShowNowPlaying(false);
            setShowQueue(true);
          }}
          onOpenEqualizer={() => {
            setShowNowPlaying(false);
            setActiveTab('EQUALIZER');
          }}
          onSendToDeckA={handleSendToDeckA}
          onSendToDeckB={handleSendToDeckB}
        />
      )}

      {/* Queue Modal */}
      {showQueue && (
        <QueueModal
          queue={queue}
          currentSong={currentSong}
          isPlaying={isPlaying}
          themeColors={themeColors}
          isArabic={isArabic}
          onClose={() => setShowQueue(false)}
          onSelectSong={(s) => handlePlaySong(s)}
          onRemoveFromQueue={(idx) => {
            const updated = [...queue];
            updated.splice(idx, 1);
            setQueue(updated);
          }}
          onClearQueue={() => setQueue([])}
          playlists={playlists}
          onAddToPlaylist={handleAddSongToPlaylist}
          onDownloadOnlineTracks={handleDownloadOnlineTracks}
          downloadProgress={downloadProgress}
          isDownloadingOnlineQueue={isDownloadingOnlineQueue}
        />
      )}

      {/* Desktop Setup & Shortcut Installer Modal */}
      <DesktopSetupModal
        isOpen={isSetupModalOpen}
        onClose={() => setIsSetupModalOpen(false)}
        themeColors={themeColors}
        isArabic={isArabic}
      />
    </div>
  );
};

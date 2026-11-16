export interface AudioItem {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: number; // in milliseconds
  uri: string; // Blob URL or remote URL or local path
  coverUri?: string;
  addedDate: number;
  /** Binary audio data for local songs, persisted in IndexedDB. */
  audioBlob?: Blob;
  /** Source metadata for streamed tracks from online providers. */
  onlineSource?: 'ALBUMATY' | 'AUDIUS' | 'OLDIES' | 'QURAN';
  sourceUrl?: string;
  downloadUri?: string;
  /** Optional failover stream URLs if primary uri fails to load. */
  fallbackUris?: string[];
  isFavorite?: boolean;
  playCount?: number;
  lastPlayed?: number;
}

export interface Playlist {
  id: string;
  name: string;
  createdAt: number;
  songCount: number;
  coverUri?: string;
}

export interface PlaylistWithSongs extends Playlist {
  songs: AudioItem[];
}

export interface RadioStation {
  id: string;
  name: string;
  streamUrls: string[];
  tags: string;
  codec: string;
  bitrate: number;
  countryCode: string;
}

export interface QuranMoshaf {
  id: number;
  name: string;
  rewayaId?: number;
  server: string;
  surahTotal: number;
  moshafType?: number;
  surahList: number[];
}

export interface QuranReciter {
  id: number;
  name: string;
  letter?: string;
  date?: string;
  moshaf: QuranMoshaf[];
}

export interface QuranSurah {
  id: number;
  name: string;
  nameAr?: string;
  nameEn?: string;
  startPage?: number;
  endPage?: number;
  makkia: boolean;
  type?: number;
}

export interface EqualizerBand {
  id: number;
  name: string;
  frequencyHz: number;
  minLevelDb: number;
  maxLevelDb: number;
  currentLevelDb: number;
}

export interface ModularEffect {
  id: string;
  displayName: string;
  isCustom?: boolean;
}

export interface CustomPreset {
  id: string;
  name: string;
  description?: string;
  tags?: string[];
  author?: string;
  bands: number[];
  bassBoost?: number;
  trebleBoost?: number;
  preampDb?: number;
  parameters?: Record<string, number>;
}

export type PlaybackMode = 'NORMAL' | 'REPEAT_ONE' | 'REPEAT_ALL' | 'SHUFFLE';

export type TabType = 
  | 'LIBRARY'
  | 'DJ_MIXER'
  | 'EQUALIZER'
  | 'KARAOKE'
  | 'RADIO'
  | 'ONLINE_MUSIC'
  | 'QURAN'
  | 'AI_STUDIO'
  | 'SETTINGS';

export type LibrarySubTab = 'ALL' | 'PLAYLISTS' | 'FAVORITES' | 'ARTISTS' | 'ALBUMS' | 'FOLDERS';

export type AppThemeOption =
  | 'SYSTEM'
  | 'DARK'
  | 'LIGHT'
  | 'DJ_BLUE'
  | 'MIDNIGHT_PURPLE'
  | 'GOLD_PREMIUM'
  | 'NEON_GREEN'
  | 'CRIMSON_RED'
  | 'CYBER_CYAN'
  | 'LIGHT_DJ_BLUE'
  | 'LIGHT_MIDNIGHT_PURPLE'
  | 'LIGHT_GOLD_PREMIUM'
  | 'LIGHT_NEON_GREEN'
  | 'LIGHT_CRIMSON_RED'
  | 'LIGHT_CYBER_CYAN';

export interface ThemeColors {
  primary: string;
  primaryHover: string;
  secondary: string;
  tertiary: string;
  background: string;
  surface: string;
  surfaceVariant: string;
  surfaceHover: string;
  border: string;
  textPrimary: string;
  textSecondary: string;
  textMuted: string;
  accentA: string; // Deck A
  accentB: string; // Deck B
  isDark: boolean;
}

export type MicFilterType =
  | 'NONE'
  | 'NORMAL'
  | 'STUDIO_REVERB'
  | 'WARM'
  | 'BRIGHT'
  | 'TELEPHONE'
  | 'ROBOT'
  | 'RADIO'
  | 'CLUB'
  | 'MEGAPHONE'
  | 'KID'
  | 'CHIPMUNK'
  | 'SMALL_WOMAN'
  | 'OLD_WOMAN'
  | 'OLD_MAN'
  | 'GIANT'
  | 'MONSTER'
  | 'CHORUS'
  | 'TREMOLO'
  | 'BASS_BOOST';

export type MicVoiceEffectType =
  | 'NONE'
  | 'WOMAN'
  | 'KID'
  | 'CHIPMUNK'
  | 'MONSTER'
  | 'DARK_DEMON'
  | 'GIANT_BASS';

export type BeatFxDivision = '1/1' | '1/2' | '1/4' | '1/8' | '3/4';

export interface SamplePad {
  id: string; // e.g. "A_0"
  bank: 'A' | 'B' | 'C' | 'D';
  index: number;
  name: string;
  category: string;
  assetPath: string;
  color?: string;
  hotkey?: string;
}

export interface AudiusTrack {
  id: string;
  title: string;
  artist: string;
  artistId: string;
  album?: string;
  artworkUrl?: string;
  streamUrl?: string;
  downloadUrl?: string;
  genre?: string;
  duration?: number;
}

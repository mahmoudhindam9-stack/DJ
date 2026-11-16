import type { PlaybackMode, ThemeColors } from '../types';

export const CONTROL_CHANNEL_NAME = 'dj-desktop-control-v1';

export interface ControlTrack {
  id: string;
  title: string;
  artist: string;
  album: string;
  coverUri?: string;
}

export interface ControlPlayerState {
  song: ControlTrack | null;
  isPlaying: boolean;
  currentTimeMs: number;
  durationMs: number;
  volume: number;
  playbackMode: PlaybackMode;
  isArabic: boolean;
  themeColors: ThemeColors;
}

export type DesktopControlCommand =
  | { action: 'toggle' }
  | { action: 'next' }
  | { action: 'previous' }
  | { action: 'seek'; ms: number }
  | { action: 'volume'; value: number }
  | { action: 'toggle-playback-mode' };

export type DesktopControlMessage =
  | { type: 'request-state' }
  | { type: 'player-state'; state: ControlPlayerState | null }
  | { type: 'command'; command: DesktopControlCommand };

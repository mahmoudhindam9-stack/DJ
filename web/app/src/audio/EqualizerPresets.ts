import { EqualizerBand } from '../types';

export const EQ_FREQUENCIES = [31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];

export const EQ_BAND_NAMES = [
  '31 Hz',
  '63 Hz',
  '125 Hz',
  '250 Hz',
  '500 Hz',
  '1 kHz',
  '2 kHz',
  '4 kHz',
  '8 kHz',
  '16 kHz',
];

export const DEFAULT_EQ_BANDS: EqualizerBand[] = EQ_FREQUENCIES.map((freq, i) => ({
  id: i,
  name: EQ_BAND_NAMES[i],
  frequencyHz: freq,
  minLevelDb: -12,
  maxLevelDb: 12,
  currentLevelDb: 0,
}));

export const BUILTIN_PRESETS: Record<string, number[]> = {
  Flat: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  'Bass Boost': [6, 5, 4, 2, 0, 0, 0, 0, 0, 0],
  'Treble Boost': [0, 0, 0, 0, 0, 0, 2, 4, 6, 7],
  Vocal: [-2, -1, 0, 3, 5, 5, 4, 2, 0, -1],
  Rock: [4, 3, 2, 0, -1, 0, 2, 4, 5, 5],
  Pop: [-1, 1, 3, 4, 4, 2, 0, 1, 3, 4],
  Jazz: [3, 2, 1, 2, -1, -1, 0, 1, 3, 3],
  Electronic: [5, 4, 2, 0, -2, 2, 1, 3, 4, 5],
  Classical: [4, 3, 2, 2, -1, -1, 0, 2, 3, 3],
  'Hip Hop': [6, 5, 3, 1, -1, -1, 1, 2, 3, 4],
  Dance: [5, 4, 2, 0, 0, 2, 3, 4, 4, 3],
  Live: [-2, 0, 2, 3, 4, 4, 4, 3, 2, 1],
  Club: [4, 4, 2, 0, 0, 0, 2, 3, 3, 2],
  Acoustic: [3, 2, 1, 1, 2, 2, 3, 3, 2, 1],
  Techno: [5, 4, 0, -2, -2, 0, 2, 4, 5, 4],
};

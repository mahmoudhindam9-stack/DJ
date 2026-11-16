import type { EqualizerBand } from '../types';

export interface GlobalEqualizerProfile {
  enabled: boolean;
  bandsDb: number[];
  bassBoost: number;
  trebleBoost: number;
  preampDb: number;
}

export interface GlobalEqualizerConnection {
  output: AudioNode;
  dispose: () => void;
}

const FREQUENCIES = [31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];

let profile: GlobalEqualizerProfile = {
  enabled: true,
  bandsDb: FREQUENCIES.map(() => 0),
  bassBoost: 0,
  trebleBoost: 0,
  preampDb: 0,
};
const subscribers = new Set<(value: GlobalEqualizerProfile) => void>();

function cloneProfile(value: GlobalEqualizerProfile): GlobalEqualizerProfile {
  return {
    enabled: Boolean(value.enabled),
    bandsDb: FREQUENCIES.map((_, index) => Math.max(-12, Math.min(12, Number(value.bandsDb[index]) || 0))),
    bassBoost: Math.max(0, Math.min(1, Number(value.bassBoost) || 0)),
    trebleBoost: Math.max(0, Math.min(1, Number(value.trebleBoost) || 0)),
    preampDb: Math.max(0, Math.min(12, Number(value.preampDb) || 0)),
  };
}

export function getGlobalEqualizerProfile(): GlobalEqualizerProfile {
  return cloneProfile(profile);
}

export function setGlobalEqualizerProfile(value: GlobalEqualizerProfile): void {
  profile = cloneProfile(value);
  const snapshot = getGlobalEqualizerProfile();
  subscribers.forEach((subscriber) => {
    try {
      subscriber(snapshot);
    } catch (error) {
      console.warn('Global equalizer subscriber failed:', error);
    }
  });
}

export function subscribeToGlobalEqualizer(
  subscriber: (value: GlobalEqualizerProfile) => void,
): () => void {
  subscribers.add(subscriber);
  return () => subscribers.delete(subscriber);
}

export function connectGlobalEqualizer(
  ctx: AudioContext,
  source: AudioNode,
  destination: AudioNode,
): GlobalEqualizerConnection {
  const preamp = ctx.createGain();
  const bands = FREQUENCIES.map((frequency) => {
    const filter = ctx.createBiquadFilter();
    filter.type = frequency <= 31 ? 'lowshelf' : frequency >= 16000 ? 'highshelf' : 'peaking';
    if (filter.type === 'peaking') filter.Q.value = 1.4;
    filter.frequency.value = frequency;
    return filter;
  });
  const bass = ctx.createBiquadFilter();
  bass.type = 'lowshelf';
  bass.frequency.value = 80;
  const treble = ctx.createBiquadFilter();
  treble.type = 'highshelf';
  treble.frequency.value = 10000;

  source.connect(preamp);
  let current: AudioNode = preamp;
  bands.forEach((filter) => {
    current.connect(filter);
    current = filter;
  });
  current.connect(bass);
  bass.connect(treble);
  treble.connect(destination);

  const apply = (next: GlobalEqualizerProfile) => {
    if (ctx.state === 'closed') return;
    const now = ctx.currentTime;
    bands.forEach((filter, index) => {
      filter.gain.setTargetAtTime(next.enabled ? next.bandsDb[index] || 0 : 0, now, 0.04);
    });
    bass.gain.setTargetAtTime(next.enabled ? next.bassBoost * 12 : 0, now, 0.04);
    treble.gain.setTargetAtTime(next.enabled ? next.trebleBoost * 12 : 0, now, 0.04);
    preamp.gain.setTargetAtTime(next.enabled ? Math.pow(10, next.preampDb / 20) : 1, now, 0.04);
  };
  apply(getGlobalEqualizerProfile());
  const unsubscribe = subscribeToGlobalEqualizer(apply);

  return {
    output: treble,
    dispose: () => {
      unsubscribe();
      [preamp, ...bands, bass, treble].forEach((node) => {
        try { node.disconnect(); } catch { /* already disconnected */ }
      });
    },
  };
}

export function profileFromBands(
  bands: EqualizerBand[],
  enabled: boolean,
  bassBoost: number,
  trebleBoost: number,
  preampDb: number,
): GlobalEqualizerProfile {
  return {
    enabled,
    bandsDb: FREQUENCIES.map((_, index) => bands[index]?.currentLevelDb || 0),
    bassBoost,
    trebleBoost,
    preampDb,
  };
}

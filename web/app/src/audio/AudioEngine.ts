import { AudioItem, EqualizerBand } from '../types';
import { DEFAULT_EQ_BANDS, BUILTIN_PRESETS } from './EqualizerPresets';
import { profileFromBands, setGlobalEqualizerProfile } from './GlobalEqualizer';

export class AudioEngine {
  private ctx: AudioContext | null = null;
  private audioElement: HTMLAudioElement;
  private sourceNode: MediaElementAudioSourceNode | null = null;
  private crossfadeAudioElement: HTMLAudioElement;
  private crossfadeSourceNode: MediaElementAudioSourceNode | null = null;
  private currentTrackGain: GainNode | null = null;
  private crossfadeTrackGain: GainNode | null = null;
  private currentTrack: AudioItem | null = null;
  private standbyTrack: AudioItem | null = null;
  private crossfadeDurationSeconds = 5;
  private crossfadeInProgress = false;
  private crossfadeDurationMs = 0;
  private crossfadeElapsedMs = 0;
  private crossfadeStartedAtMs = 0;
  private crossfadeTimer: ReturnType<typeof setTimeout> | null = null;
  private crossfadeTrackId: string | null = null;
  private crossfadeTrackUri: string | null = null;

  // Processing chain nodes
  private preampGain: GainNode | null = null;
  private eqFilters: BiquadFilterNode[] = [];
  private bassBoostFilter: BiquadFilterNode | null = null;
  private trebleBoostFilter: BiquadFilterNode | null = null;
  private virtualizerPanner: StereoPannerNode | null = null;
  private masterGain: GainNode | null = null;
  private analyser: AnalyserNode | null = null;
  private masterVolume = 0.85;

  // State
  private isInitialized = false;
  private isEqEnabled = true;
  private bands: EqualizerBand[] = JSON.parse(JSON.stringify(DEFAULT_EQ_BANDS));
  private selectedPreset = 'Flat';
  private bassBoost = 0; // 0 to 1
  private trebleBoost = 0; // 0 to 1
  private preampDb = 0; // 0 to 12
  private virtualizer3D = false;

  // Listeners
  private onTimeUpdateListeners: Array<(currentTime: number, duration: number) => void> = [];
  private onStateChangeListeners: Array<(isPlaying: boolean) => void> = [];
  private onEndedListeners: Array<() => void> = [];
  private onTrackTransitionListeners: Array<(track: AudioItem) => void> = [];

  private readonly primaryTimeUpdateHandler = () => {
    const cur = this.audioElement.currentTime * 1000;
    const dur = this.durationMs;
    this.onTimeUpdateListeners.forEach((fn) => fn(cur, dur));
    this.checkAutomaticCrossfade();
  };

  private readonly crossfadeReadyHandler = () => {
    if (this.isPlaying) this.checkAutomaticCrossfade();
  };

  private readonly primaryPlayHandler = () => {
    this.onStateChangeListeners.forEach((fn) => fn(true));
  };

  private readonly primaryPauseHandler = () => {
    this.onStateChangeListeners.forEach((fn) => fn(false));
  };

  private readonly primaryEndedHandler = () => {
    if (this.crossfadeInProgress && this.standbyTrack && !this.crossfadeAudioElement.paused) {
      this.finishCrossfade();
      return;
    }
    if (this.crossfadeInProgress) this.cancelCrossfade();
    this.onEndedListeners.forEach((fn) => fn());
  };

  private fallbackIndex = 0;

  private readonly primaryErrorHandler = () => {
    const fallbacks = this.currentTrack?.fallbackUris;
    if (fallbacks && this.fallbackIndex < fallbacks.length) {
      const nextUri = fallbacks[this.fallbackIndex++];
      if (nextUri) {
        console.warn(`Primary stream failed; failing over to backup stream (${this.fallbackIndex}/${fallbacks.length}):`, nextUri);
        this.audioElement.src = nextUri;
        this.audioElement.load();
        void this.audioElement.play().catch(() => {});
      }
    }
  };

  constructor() {
    this.audioElement = new Audio();
    this.audioElement.preload = 'auto';
    this.audioElement.crossOrigin = 'anonymous';

    this.crossfadeAudioElement = new Audio();
    this.crossfadeAudioElement.preload = 'auto';
    this.crossfadeAudioElement.crossOrigin = 'anonymous';
    this.crossfadeAudioElement.addEventListener('canplay', this.crossfadeReadyHandler);
    this.attachPrimaryListeners();
    this.publishGlobalEqProfile();
  }

  private attachPrimaryListeners(): void {
    this.audioElement.addEventListener('timeupdate', this.primaryTimeUpdateHandler);
    this.audioElement.addEventListener('play', this.primaryPlayHandler);
    this.audioElement.addEventListener('pause', this.primaryPauseHandler);
    this.audioElement.addEventListener('ended', this.primaryEndedHandler);
    this.audioElement.addEventListener('error', this.primaryErrorHandler);
  }

  private detachPrimaryListeners(): void {
    this.audioElement.removeEventListener('timeupdate', this.primaryTimeUpdateHandler);
    this.audioElement.removeEventListener('play', this.primaryPlayHandler);
    this.audioElement.removeEventListener('pause', this.primaryPauseHandler);
    this.audioElement.removeEventListener('ended', this.primaryEndedHandler);
    this.audioElement.removeEventListener('error', this.primaryErrorHandler);
  }

  private ensureAudioContext(): AudioContext {
    if (!this.ctx) {
      const AudioCtxClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.ctx = new AudioCtxClass();
    }
    if (this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
    if (!this.isInitialized && this.ctx) {
      this.setupAudioGraph(this.ctx);
      this.isInitialized = true;
    }
    return this.ctx;
  }

  private setupAudioGraph(ctx: AudioContext) {
    try {
      this.sourceNode = ctx.createMediaElementSource(this.audioElement);
      this.crossfadeSourceNode = ctx.createMediaElementSource(this.crossfadeAudioElement);
      this.currentTrackGain = ctx.createGain();
      this.currentTrackGain.gain.value = 1;
      this.crossfadeTrackGain = ctx.createGain();
      this.crossfadeTrackGain.gain.value = 0;
      this.preampGain = ctx.createGain();
      this.preampGain.gain.value = Math.pow(10, this.preampDb / 20);

      // 10-band peaking filters
      this.eqFilters = this.bands.map((band) => {
        const filter = ctx.createBiquadFilter();
        if (band.frequencyHz <= 31) {
          filter.type = 'lowshelf';
        } else if (band.frequencyHz >= 16000) {
          filter.type = 'highshelf';
        } else {
          filter.type = 'peaking';
          filter.Q.value = 1.4;
        }
        filter.frequency.value = band.frequencyHz;
        filter.gain.value = this.isEqEnabled ? band.currentLevelDb : 0;
        return filter;
      });

      // Bass Boost (lowshelf at 80Hz)
      this.bassBoostFilter = ctx.createBiquadFilter();
      this.bassBoostFilter.type = 'lowshelf';
      this.bassBoostFilter.frequency.value = 80;
      this.bassBoostFilter.gain.value = this.isEqEnabled ? this.bassBoost * 12 : 0;

      // Treble Boost (highshelf at 10kHz)
      this.trebleBoostFilter = ctx.createBiquadFilter();
      this.trebleBoostFilter.type = 'highshelf';
      this.trebleBoostFilter.frequency.value = 10000;
      this.trebleBoostFilter.gain.value = this.isEqEnabled ? this.trebleBoost * 12 : 0;

      // Stereo Virtualizer
      if (ctx.createStereoPanner) {
        this.virtualizerPanner = ctx.createStereoPanner();
        this.virtualizerPanner.pan.value = 0;
      }

      this.masterGain = ctx.createGain();
      this.masterGain.gain.value = this.masterVolume;

      this.analyser = ctx.createAnalyser();
      this.analyser.fftSize = 128;
      this.analyser.smoothingTimeConstant = 0.8;

      // Both tracks feed the same EQ/master chain through independent crossfader gains.
      this.sourceNode.connect(this.currentTrackGain);
      this.crossfadeSourceNode.connect(this.crossfadeTrackGain);
      this.currentTrackGain.connect(this.preampGain);
      this.crossfadeTrackGain.connect(this.preampGain);

      let current: AudioNode = this.preampGain;

      for (const f of this.eqFilters) {
        current.connect(f);
        current = f;
      }

      current.connect(this.bassBoostFilter);
      current = this.bassBoostFilter;

      current.connect(this.trebleBoostFilter);
      current = this.trebleBoostFilter;

      if (this.virtualizerPanner) {
        current.connect(this.virtualizerPanner);
        current = this.virtualizerPanner;
      }

      current.connect(this.masterGain);
      this.masterGain.connect(this.analyser);
      this.analyser.connect(ctx.destination);
    } catch (e) {
      console.warn('Web Audio Graph initialization note:', e);
    }
  }

  // --- PLAYBACK ---
  async loadTrack(item: AudioItem): Promise<void> {
    this.cancelCrossfade();
    this.audioElement.pause();
    this.crossfadeAudioElement.pause();
    this.crossfadeAudioElement.removeAttribute('src');
    this.crossfadeAudioElement.load();
    this.currentTrack = item;
    this.fallbackIndex = 0;
    this.standbyTrack = null;
    this.crossfadeTrackId = null;
    this.crossfadeTrackUri = null;
    this.ensureAudioContext();
    this.audioElement.src = item.uri;
    this.audioElement.load();
    this.setGainImmediately(this.currentTrackGain, 1);
    this.setGainImmediately(this.crossfadeTrackGain, 0);
  }

  /** Preload the next song on a silent second source for a real automatic overlap. */
  loadCrossfadeTrack(item: AudioItem | null): void {
    if (!item || this.crossfadeDurationSeconds <= 0 || item.id === this.currentTrack?.id) {
      if (this.crossfadeInProgress) this.cancelCrossfade();
      this.standbyTrack = null;
      this.crossfadeTrackId = null;
      this.crossfadeTrackUri = null;
      this.crossfadeAudioElement.pause();
      this.crossfadeAudioElement.removeAttribute('src');
      this.crossfadeAudioElement.load();
      this.setGainImmediately(this.crossfadeTrackGain, 0);
      return;
    }
    if (this.crossfadeTrackId === item.id && this.crossfadeTrackUri === item.uri) {
      this.standbyTrack = item;
      return;
    }
    if (this.crossfadeInProgress) this.cancelCrossfade();
    this.crossfadeAudioElement.pause();
    this.ensureAudioContext();
    this.standbyTrack = item;
    this.crossfadeTrackId = item.id;
    this.crossfadeTrackUri = item.uri;
    this.crossfadeAudioElement.src = item.uri;
    this.crossfadeAudioElement.load();
    this.setGainImmediately(this.crossfadeTrackGain, 0);
  }

  /** Set the natural overlap between sequential songs, in whole seconds (0–15). */
  setCrossfadeDuration(seconds: number): void {
    const next = Math.max(0, Math.min(15, Math.round(Number.isFinite(seconds) ? seconds : 0)));
    this.crossfadeDurationSeconds = next;
    if (next === 0 && this.crossfadeInProgress) this.cancelCrossfade();
  }

  get crossfadeDuration(): number {
    return this.crossfadeDurationSeconds;
  }

  private setGainImmediately(node: GainNode | null, value: number): void {
    if (!node || !this.ctx) return;
    const now = this.ctx.currentTime;
    node.gain.cancelScheduledValues(now);
    node.gain.setValueAtTime(value, now);
  }

  private clearCrossfadeTimer(): void {
    if (this.crossfadeTimer !== null) {
      clearTimeout(this.crossfadeTimer);
      this.crossfadeTimer = null;
    }
  }

  private getCrossfadeElapsedMs(): number {
    return this.crossfadeElapsedMs +
      (this.crossfadeStartedAtMs > 0 ? Math.max(0, Date.now() - this.crossfadeStartedAtMs) : 0);
  }

  private cancelCrossfade(): void {
    this.clearCrossfadeTimer();
    this.crossfadeInProgress = false;
    this.crossfadeDurationMs = 0;
    this.crossfadeElapsedMs = 0;
    this.crossfadeStartedAtMs = 0;
    this.crossfadeAudioElement.pause();
    try { this.crossfadeAudioElement.currentTime = 0; } catch { /* metadata may not be ready */ }
    this.setGainImmediately(this.currentTrackGain, 1);
    this.setGainImmediately(this.crossfadeTrackGain, 0);
  }

  private checkAutomaticCrossfade(): void {
    if (
      this.crossfadeDurationSeconds <= 0 || this.crossfadeInProgress || !this.isPlaying ||
      !this.currentTrack || !this.standbyTrack || this.standbyTrack.id === this.currentTrack.id
    ) return;

    // Some online streams expose NaN/Infinity duration while their metadata has a duration.
    const mediaDurationMs = (this.audioElement.duration || 0) * 1000;
    const totalMs = Number.isFinite(mediaDurationMs) && mediaDurationMs > 0
      ? mediaDurationMs
      : (this.currentTrack.duration || 0);
    if (!Number.isFinite(totalMs) || totalMs <= 0) return;
    const remainingMs = totalMs - this.currentTimeMs;
    if (remainingMs <= 0 || remainingMs > this.crossfadeDurationSeconds * 1000) return;

    // Do not start a ramp while the next source is still silent/loading; retry on canplay.
    if (this.crossfadeAudioElement.readyState < 2) return;
    void this.beginAutomaticCrossfade(remainingMs);
  }

  private async beginAutomaticCrossfade(remainingMs: number): Promise<void> {
    if (this.crossfadeInProgress || !this.standbyTrack || this.crossfadeDurationSeconds <= 0) return;
    this.crossfadeInProgress = true;
    this.crossfadeDurationMs = Math.max(1, Math.min(remainingMs, this.crossfadeDurationSeconds * 1000));
    this.crossfadeElapsedMs = 0;
    this.crossfadeStartedAtMs = 0;
    try {
      const context = this.ensureAudioContext();
      if (context.state === 'suspended') await context.resume();
      this.crossfadeAudioElement.currentTime = 0;
      await this.crossfadeAudioElement.play();
    } catch (error) {
      console.warn('Automatic crossfade could not start:', error);
      this.cancelCrossfade();
      return;
    }
    if (!this.crossfadeInProgress || !this.standbyTrack) return;
    if (!this.isPlaying) {
      // The current track may have been paused while the standby player was loading.
      // Keep the pending transition ready to resume, but do not run a timer while paused.
      this.crossfadeAudioElement.pause();
      this.setGainImmediately(this.currentTrackGain, 1);
      this.setGainImmediately(this.crossfadeTrackGain, 0);
      return;
    }
    this.scheduleCrossfadeRamp(0);
  }

  private scheduleCrossfadeRamp(progress: number): void {
    const context = this.ensureAudioContext();
    const now = context.currentTime;
    const normalized = Math.max(0, Math.min(1, progress));
    const elapsedMs = normalized * this.crossfadeDurationMs;
    const remainingMs = Math.max(0, this.crossfadeDurationMs - elapsedMs);
    const curveLength = 96;
    const currentCurve = new Float32Array(curveLength);
    const standbyCurve = new Float32Array(curveLength);
    for (let index = 0; index < curveLength; index += 1) {
      const position = normalized + (1 - normalized) * (index / (curveLength - 1));
      currentCurve[index] = Math.cos(position * Math.PI / 2);
      standbyCurve[index] = Math.sin(position * Math.PI / 2);
    }
    for (const [node, curve] of [
      [this.currentTrackGain, currentCurve],
      [this.crossfadeTrackGain, standbyCurve],
    ] as Array<[GainNode | null, Float32Array]>) {
      if (!node) continue;
      node.gain.cancelScheduledValues(now);
      if (remainingMs <= 8) node.gain.setValueAtTime(curve[curve.length - 1], now);
      else node.gain.setValueCurveAtTime(curve, now, remainingMs / 1000);
    }
    this.crossfadeElapsedMs = elapsedMs;
    this.crossfadeStartedAtMs = Date.now();
    this.clearCrossfadeTimer();
    this.crossfadeTimer = setTimeout(() => this.finishCrossfade(), remainingMs);
  }

  private finishCrossfade(): void {
    const nextTrack = this.standbyTrack;
    if (!this.crossfadeInProgress || !nextTrack) return;
    this.clearCrossfadeTimer();
    this.detachPrimaryListeners();
    const oldPrimaryAudio = this.audioElement;
    oldPrimaryAudio.pause();

    // Promote the already-playing secondary source, retaining its elapsed time.
    this.audioElement = this.crossfadeAudioElement;
    this.crossfadeAudioElement = oldPrimaryAudio;
    const oldPrimarySource = this.sourceNode;
    this.sourceNode = this.crossfadeSourceNode;
    this.crossfadeSourceNode = oldPrimarySource;
    const oldPrimaryGain = this.currentTrackGain;
    this.currentTrackGain = this.crossfadeTrackGain;
    this.crossfadeTrackGain = oldPrimaryGain;

    this.currentTrack = nextTrack;
    this.standbyTrack = null;
    this.crossfadeTrackId = null;
    this.crossfadeTrackUri = null;
    this.crossfadeInProgress = false;
    this.crossfadeDurationMs = 0;
    this.crossfadeElapsedMs = 0;
    this.crossfadeStartedAtMs = 0;
    this.setGainImmediately(this.currentTrackGain, 1);
    this.setGainImmediately(this.crossfadeTrackGain, 0);

    this.crossfadeAudioElement.pause();
    this.crossfadeAudioElement.removeAttribute('src');
    this.crossfadeAudioElement.load();
    this.attachPrimaryListeners();
    this.onTrackTransitionListeners.forEach((listener) => {
      try { listener(nextTrack); } catch (error) { console.warn('Crossfade transition listener failed:', error); }
    });
  }

  async play(): Promise<void> {
    const context = this.ensureAudioContext();
    try {
      if (context.state === 'suspended') await context.resume();
      await this.audioElement.play();
      if (this.crossfadeInProgress && this.standbyTrack) {
        await this.crossfadeAudioElement.play();
        const progress = this.crossfadeDurationMs > 0
          ? this.getCrossfadeElapsedMs() / this.crossfadeDurationMs
          : 0;
        this.scheduleCrossfadeRamp(progress);
      }
    } catch (err) {
      console.warn('Audio playback waiting for interaction:', err);
    }
  }

  pause(): void {
    if (this.crossfadeInProgress) {
      this.crossfadeElapsedMs = Math.min(this.crossfadeDurationMs, this.getCrossfadeElapsedMs());
      this.crossfadeStartedAtMs = 0;
      this.clearCrossfadeTimer();
      const progress = this.crossfadeDurationMs > 0 ? this.crossfadeElapsedMs / this.crossfadeDurationMs : 0;
      this.setGainImmediately(this.currentTrackGain, Math.cos(progress * Math.PI / 2));
      this.setGainImmediately(this.crossfadeTrackGain, Math.sin(progress * Math.PI / 2));
    }
    this.audioElement.pause();
    this.crossfadeAudioElement.pause();
  }

  seekTo(positionMs: number): void {
    if (!Number.isNaN(positionMs) && Number.isFinite(positionMs) && positionMs >= 0) {
      if (this.crossfadeInProgress) this.cancelCrossfade();
      this.audioElement.currentTime = positionMs / 1000;
    }
  }

  setVolume(volume: number): void {
    this.masterVolume = Math.max(0, Math.min(1, volume));
    this.audioElement.volume = 1;
    if (this.masterGain && this.ctx) this.masterGain.gain.setTargetAtTime(this.masterVolume, this.ctx.currentTime, 0.02);
  }

  setPlaybackRate(rate: number): void {
    this.audioElement.playbackRate = Math.max(0.25, Math.min(2.0, rate));
  }

  get isPlaying(): boolean { return !this.audioElement.paused && !this.audioElement.ended; }
  get currentTimeMs(): number { return (this.audioElement.currentTime || 0) * 1000; }
  get durationMs(): number {
    const mediaDurationMs = (this.audioElement.duration || 0) * 1000;
    if (Number.isFinite(mediaDurationMs) && mediaDurationMs > 0) return mediaDurationMs;
    return this.currentTrack?.duration || 0;
  }

  // --- EQUALIZER & EFFECTS ---
  get eqEnabled(): boolean {
    return this.isEqEnabled;
  }

  setEqEnabled(enabled: boolean): void {
    this.isEqEnabled = enabled;
    this.applyAllFilters();
    this.publishGlobalEqProfile();
  }

  getBands(): EqualizerBand[] {
    return this.bands;
  }

  setBandLevel(bandId: number, levelDb: number): void {
    const band = this.bands.find((b) => b.id === bandId);
    if (!band) return;
    band.currentLevelDb = Math.max(band.minLevelDb, Math.min(band.maxLevelDb, levelDb));
    this.selectedPreset = 'Custom';

    if (this.eqFilters[bandId] && this.ctx) {
      const target = this.isEqEnabled ? band.currentLevelDb : 0;
      this.eqFilters[bandId].gain.setTargetAtTime(target, this.ctx.currentTime, 0.05);
    }
    this.publishGlobalEqProfile();
  }

  get currentPreset(): string {
    return this.selectedPreset;
  }

  applyPreset(presetName: string): void {
    const presetValues = BUILTIN_PRESETS[presetName];
    if (!presetValues) return;

    this.selectedPreset = presetName;
    presetValues.forEach((val, i) => {
      if (this.bands[i]) {
        this.bands[i].currentLevelDb = val;
        if (this.eqFilters[i] && this.ctx) {
          const target = this.isEqEnabled ? val : 0;
          this.eqFilters[i].gain.setTargetAtTime(target, this.ctx.currentTime, 0.05);
        }
      }
    });
    this.publishGlobalEqProfile();
  }

  applyCustomBands(values: number[]): void {
    this.selectedPreset = 'Custom';
    values.forEach((val, i) => {
      if (this.bands[i]) {
        this.bands[i].currentLevelDb = val;
        if (this.eqFilters[i] && this.ctx) {
          const target = this.isEqEnabled ? val : 0;
          this.eqFilters[i].gain.setTargetAtTime(target, this.ctx.currentTime, 0.05);
        }
      }
    });
    this.publishGlobalEqProfile();
  }

  get bassBoostLevel(): number {
    return this.bassBoost;
  }

  setBassBoostLevel(val: number): void {
    this.bassBoost = Math.max(0, Math.min(1, val));
    if (this.bassBoostFilter && this.ctx) {
      const target = this.isEqEnabled ? this.bassBoost * 12 : 0;
      this.bassBoostFilter.gain.setTargetAtTime(target, this.ctx.currentTime, 0.05);
    }
    this.publishGlobalEqProfile();
  }

  get trebleBoostLevel(): number {
    return this.trebleBoost;
  }

  setTrebleBoostLevel(val: number): void {
    this.trebleBoost = Math.max(0, Math.min(1, val));
    if (this.trebleBoostFilter && this.ctx) {
      const target = this.isEqEnabled ? this.trebleBoost * 12 : 0;
      this.trebleBoostFilter.gain.setTargetAtTime(target, this.ctx.currentTime, 0.05);
    }
    this.publishGlobalEqProfile();
  }

  get currentPreampDb(): number {
    return this.preampDb;
  }

  setPreampDb(val: number): void {
    this.preampDb = Math.max(0, Math.min(12, val));
    if (this.preampGain && this.ctx) {
      const gainMultiplier = this.isEqEnabled ? Math.pow(10, this.preampDb / 20) : 1;
      this.preampGain.gain.setTargetAtTime(gainMultiplier, this.ctx.currentTime, 0.05);
    }
    this.publishGlobalEqProfile();
  }

  private publishGlobalEqProfile(): void {
    setGlobalEqualizerProfile(profileFromBands(
      this.bands,
      this.isEqEnabled,
      this.bassBoost,
      this.trebleBoost,
      this.preampDb,
    ));
  }

  private applyAllFilters(): void {
    const ctx = this.ctx;
    if (!ctx) return;
    const now = ctx.currentTime;
    this.bands.forEach((b, i) => {
      if (this.eqFilters[i]) {
        const target = this.isEqEnabled ? b.currentLevelDb : 0;
        this.eqFilters[i].gain.setTargetAtTime(target, now, 0.05);
      }
    });
    if (this.bassBoostFilter) {
      this.bassBoostFilter.gain.setTargetAtTime(
        this.isEqEnabled ? this.bassBoost * 12 : 0,
        now,
        0.05
      );
    }
    if (this.trebleBoostFilter) {
      this.trebleBoostFilter.gain.setTargetAtTime(
        this.isEqEnabled ? this.trebleBoost * 12 : 0,
        now,
        0.05
      );
    }
    if (this.preampGain) {
      const gainMultiplier = this.isEqEnabled ? Math.pow(10, this.preampDb / 20) : 1;
      this.preampGain.gain.setTargetAtTime(gainMultiplier, now, 0.05);
    }
  }

  getVisualizerData(): Uint8Array {
    if (!this.analyser) return new Uint8Array(64);
    const data = new Uint8Array(this.analyser.frequencyBinCount);
    this.analyser.getByteFrequencyData(data);
    return data;
  }

  // --- LISTENERS ---
  onTimeUpdate(fn: (currentTime: number, duration: number) => void): () => void {
    this.onTimeUpdateListeners.push(fn);
    return () => {
      this.onTimeUpdateListeners = this.onTimeUpdateListeners.filter((l) => l !== fn);
    };
  }

  onStateChange(fn: (isPlaying: boolean) => void): () => void {
    this.onStateChangeListeners.push(fn);
    return () => {
      this.onStateChangeListeners = this.onStateChangeListeners.filter((l) => l !== fn);
    };
  }

  onEnded(fn: () => void): () => void {
    this.onEndedListeners.push(fn);
    return () => {
      this.onEndedListeners = this.onEndedListeners.filter((l) => l !== fn);
    };
  }

  onTrackTransition(fn: (track: AudioItem) => void): () => void {
    this.onTrackTransitionListeners.push(fn);
    return () => {
      this.onTrackTransitionListeners = this.onTrackTransitionListeners.filter((listener) => listener !== fn);
    };
  }
}

export const mainAudioEngine = new AudioEngine();

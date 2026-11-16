import { AudioItem } from '../types';
import { connectGlobalEqualizer, GlobalEqualizerConnection } from './GlobalEqualizer';

export type DJEffectType =
  | 'none'
  | 'fx_filter'
  | 'fx_delay'
  | 'fx_reverb'
  | 'fx_flanger'
  | 'fx_phaser'
  | 'fx_bitcrush'
  | 'fx_distortion'
  | 'fx_compressor'
  | 'voice_chipmunk'
  | 'voice_robot'
  | 'voice_monster'
  | 'voice_demon'
  | 'voice_giant'
  | 'voice_kid'
  | 'voice_woman'
  | 'voice_telephone'
  | 'voice_radio'
  | 'voice_alien';

const clamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(max, Number.isFinite(value) ? value : min));

const VOICE_RATE: Partial<Record<DJEffectType, number>> = {
  voice_woman: 1.22,
  voice_kid: 1.42,
  voice_chipmunk: 1.75,
  voice_monster: 0.72,
  voice_demon: 0.62,
  voice_giant: 0.52,
};

export class DJDeckEngine {
  public readonly deckName: string;
  private ctx: AudioContext | null = null;
  private audioElement: HTMLAudioElement;
  private sourceNode: MediaElementAudioSourceNode | null = null;

  private deckGain: GainNode | null = null;
  private fxSendGain: GainNode | null = null;
  private globalEqConnection: GlobalEqualizerConnection | null = null;
  private crossfadeGain: GainNode | null = null;
  private filterNode: BiquadFilterNode | null = null;
  private voiceFormantFilter: BiquadFilterNode | null = null;
  private robotOscillator: OscillatorNode | null = null;
  private robotGain: GainNode | null = null;
  private robotModulatorGain: GainNode | null = null;
  private delayNode: DelayNode | null = null;
  private delayFeedback: GainNode | null = null;
  private delayWetGain: GainNode | null = null;
  private delayLfo: OscillatorNode | null = null;
  private delayLfoGain: GainNode | null = null;
  private reverbNode: ConvolverNode | null = null;
  private reverbWetGain: GainNode | null = null;
  private waveshaperNode: WaveShaperNode | null = null;
  private compressorNode: DynamicsCompressorNode | null = null;
  private analyser: AnalyserNode | null = null;
  private bpmAnalyser: AnalyserNode | null = null;
  private bpmSilentGain: GainNode | null = null;
  private scratchFilter: BiquadFilterNode | null = null;
  private scratchGain: GainNode | null = null;
  private scratchNoiseBuffer: AudioBuffer | null = null;
  private scratchNoiseSource: AudioBufferSourceNode | null = null;
  private scratchOscillator: OscillatorNode | null = null;
  private scratchOscillatorGain: GainNode | null = null;
  private scratchActive = false;
  private scratchWasPlaying = false;
  private bpmFrameId: number | null = null;
  private bpmLastSampleAt = 0;
  private bpmLastEvaluationAt = 0;
  private bpmSampleIntervalMs = 50;
  private bpmPreviousSpectrum = new Uint8Array(0);
  private bpmSamples: number[] = [];
  private bpmCandidate: number | null = null;
  private bpmCandidateHits = 0;

  public currentBpm: number | null = null;
  public bpmConfidence = 0;
  public currentTrack: AudioItem | null = null;
  public pitch = 1.0;
  public cuePositionMs = 0;
  public loopBeats = 0;
  private loopStartMs = 0;
  private loopEndMs = 0;
  private onTrackEndedCallbacks: Array<() => void> = [];
  public activeEffect: DJEffectType = 'none';
  public fxAmount = 0.5;
  public isPlaying = false;
  public volume = 1.0;
  private crossfadeVolume = 1.0;
  private fallbackIndex = 0;
  private onUpdateCallbacks: Array<() => void> = [];

  constructor(name: string) {
    this.deckName = name;
    this.audioElement = new Audio();
    this.audioElement.preload = 'auto';
    this.audioElement.crossOrigin = 'anonymous';
    this.audioElement.volume = 1;

    this.enforcePitchPreservation();

    this.audioElement.addEventListener('play', () => {
      this.enforcePitchPreservation();
      this.isPlaying = true;
      this.startBpmDetection();
      this.notify();
    });
    this.audioElement.addEventListener('pause', () => {
      this.isPlaying = false;
      this.stopBpmDetection();
      this.notify();
    });
    this.audioElement.addEventListener('loadedmetadata', () => {
      this.enforcePitchPreservation();
      this.applyPitchAndEffect();
      this.notify();
    });
    this.audioElement.addEventListener('canplay', () => {
      this.enforcePitchPreservation();
      this.applyPitchAndEffect();
      this.notify();
    });
    this.audioElement.addEventListener('ended', () => {
      if (this.scratchActive) this.endScratch();
      this.isPlaying = false;
      this.stopBpmDetection();
      this.notify();
      this.onTrackEndedCallbacks.forEach((callback) => {
        try { callback(); } catch (error) { console.warn('DJ deck auto-fill callback failed:', error); }
      });
    });
    this.audioElement.addEventListener('error', () => {
      const fallbacks = this.currentTrack?.fallbackUris;
      if (fallbacks && this.fallbackIndex < fallbacks.length) {
        const nextUri = fallbacks[this.fallbackIndex++];
        if (nextUri) {
          console.warn(`DJ Deck ${this.deckName} stream failed; failing over to backup stream:`, nextUri);
          this.audioElement.src = nextUri;
          this.audioElement.load();
          this.enforcePitchPreservation();
          this.applyPitchAndEffect();
          void this.audioElement.play().catch(() => {});
        }
      }
    });
    this.audioElement.addEventListener('timeupdate', () => this.notify());
    this.audioElement.addEventListener('durationchange', () => this.notify());
    this.audioElement.addEventListener('ratechange', () => {
      this.enforcePitchPreservation();
      this.notify();
    });
  }

  public enforcePitchPreservation() {
    const media = this.audioElement as HTMLAudioElement & {
      preservesPitch?: boolean;
      webkitPreservesPitch?: boolean;
      mozPreservesPitch?: boolean;
    };
    media.preservesPitch = false;
    media.webkitPreservesPitch = false;
    media.mozPreservesPitch = false;
  }

  private ensureContext(): AudioContext {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.ctx = new AudioCtx();
    }
    if (this.ctx.state === 'suspended') {
      void this.ctx.resume().catch(() => {});
    }
    if (!this.sourceNode) {
      this.setupNodes(this.ctx);
      this.applyPitchAndEffect();
    }
    return this.ctx;
  }

  private setupNodes(ctx: AudioContext) {
    this.sourceNode = ctx.createMediaElementSource(this.audioElement);

    // Robot & alien amplitude modulator
    this.robotOscillator = ctx.createOscillator();
    this.robotOscillator.type = 'sine';
    this.robotOscillator.frequency.value = 140;
    this.robotGain = ctx.createGain();
    this.robotGain.gain.value = 0;
    this.robotModulatorGain = ctx.createGain();
    this.robotModulatorGain.gain.value = 1;
    this.robotOscillator.connect(this.robotGain);
    this.robotGain.connect(this.robotModulatorGain.gain);
    this.robotOscillator.start();

    this.filterNode = ctx.createBiquadFilter();
    this.filterNode.type = 'allpass';
    this.filterNode.frequency.value = 1000;
    this.filterNode.Q.value = 0.0001;

    this.voiceFormantFilter = ctx.createBiquadFilter();
    this.voiceFormantFilter.type = 'allpass';
    this.voiceFormantFilter.frequency.value = 1000;
    this.voiceFormantFilter.Q.value = 1;

    this.waveshaperNode = ctx.createWaveShaper();
    this.waveshaperNode.curve = this.makeDistortionCurve(0);
    this.waveshaperNode.oversample = '4x';

    this.compressorNode = ctx.createDynamicsCompressor();
    this.compressorNode.threshold.value = -18;
    this.compressorNode.knee.value = 20;
    this.compressorNode.ratio.value = 3;
    this.compressorNode.attack.value = 0.015;
    this.compressorNode.release.value = 0.25;

    this.deckGain = ctx.createGain();
    this.deckGain.gain.value = this.volume;

    // Independent post-DSP FX send keeps wet returns on the same deck output path.
    this.fxSendGain = ctx.createGain();
    this.fxSendGain.gain.value = 0.92;

    this.crossfadeGain = ctx.createGain();
    this.crossfadeGain.gain.value = this.crossfadeVolume;

    // Dedicated vinyl friction path: filtered noise and a subdued resonant tone
    // are mixed through the deck crossfader so scratching follows the mixer.
    this.scratchFilter = ctx.createBiquadFilter();
    this.scratchFilter.type = 'bandpass';
    this.scratchFilter.frequency.value = 1300;
    this.scratchFilter.Q.value = 1.35;
    this.scratchGain = ctx.createGain();
    this.scratchGain.gain.value = 0;
    this.scratchNoiseBuffer = ctx.createBuffer(1, Math.max(1, Math.floor(ctx.sampleRate)), ctx.sampleRate);
    const scratchNoise = this.scratchNoiseBuffer.getChannelData(0);
    let filteredNoise = 0;
    for (let index = 0; index < scratchNoise.length; index += 1) {
      filteredNoise = filteredNoise * 0.32 + (Math.random() * 2 - 1) * 0.68;
      scratchNoise[index] = filteredNoise * 0.65;
    }
    this.scratchOscillator = ctx.createOscillator();
    this.scratchOscillator.type = 'triangle';
    this.scratchOscillator.frequency.value = 110;
    this.scratchOscillatorGain = ctx.createGain();
    this.scratchOscillatorGain.gain.value = 0;
    this.scratchFilter.connect(this.scratchGain);
    this.scratchGain.connect(this.crossfadeGain);
    this.scratchOscillator.connect(this.scratchOscillatorGain);
    this.scratchOscillatorGain.connect(this.crossfadeGain);
    this.scratchOscillator.start();

    this.analyser = ctx.createAnalyser();
    this.analyser.fftSize = 256;

    // Use a dedicated high-resolution analyser for beat tracking. Its output is
    // muted, so it observes the same source without adding a second audible path.
    this.bpmAnalyser = ctx.createAnalyser();
    this.bpmAnalyser.fftSize = 2048;
    this.bpmAnalyser.smoothingTimeConstant = 0;
    this.bpmAnalyser.minDecibels = -90;
    this.bpmAnalyser.maxDecibels = -20;
    this.bpmPreviousSpectrum = new Uint8Array(this.bpmAnalyser.frequencyBinCount);
    this.bpmSilentGain = ctx.createGain();
    this.bpmSilentGain.gain.value = 0;
    this.sourceNode.connect(this.bpmAnalyser);
    this.bpmAnalyser.connect(this.bpmSilentGain);
    this.bpmSilentGain.connect(ctx.destination);

    this.delayNode = ctx.createDelay(2);
    this.delayNode.delayTime.value = 0.25;
    this.delayFeedback = ctx.createGain();
    this.delayFeedback.gain.value = 0;
    this.delayWetGain = ctx.createGain();
    this.delayWetGain.gain.value = 0;

    this.delayLfo = ctx.createOscillator();
    this.delayLfo.frequency.value = 0.25;
    this.delayLfoGain = ctx.createGain();
    this.delayLfoGain.gain.value = 0;
    this.delayLfo.connect(this.delayLfoGain);
    this.delayLfoGain.connect(this.delayNode.delayTime);
    this.delayLfo.start();

    this.reverbNode = ctx.createConvolver();
    this.reverbNode.buffer = this.createImpulseResponse(ctx, 3.1, 2.15);
    this.reverbWetGain = ctx.createGain();
    this.reverbWetGain.gain.value = 0;

    // Main dry path through voice/fx processors
    this.sourceNode.connect(this.robotModulatorGain);
    this.robotModulatorGain.connect(this.filterNode);
    this.filterNode.connect(this.voiceFormantFilter);
    this.voiceFormantFilter.connect(this.waveshaperNode);
    this.waveshaperNode.connect(this.compressorNode);
    this.compressorNode.connect(this.deckGain);
    this.compressorNode.connect(this.fxSendGain);

    // Send the post-filter, post-saturation signal into the effect returns.
    this.fxSendGain.connect(this.delayNode);
    this.delayNode.connect(this.delayFeedback);
    this.delayFeedback.connect(this.delayNode);
    this.delayNode.connect(this.delayWetGain);
    this.delayWetGain.connect(this.deckGain);

    this.fxSendGain.connect(this.reverbNode);
    this.reverbNode.connect(this.reverbWetGain);
    this.reverbWetGain.connect(this.deckGain);

    this.deckGain.connect(this.crossfadeGain);
    // The shared 10-band EQ is after both deck FX and the DJ crossfader so
    // scratches, echo/reverb returns and both channels respond to the same EQ.
    if (this.crossfadeGain && this.analyser) {
      this.globalEqConnection = connectGlobalEqualizer(ctx, this.crossfadeGain, this.analyser);
    }
    this.analyser.connect(ctx.destination);
  }

  private createImpulseResponse(ctx: AudioContext, seconds: number, decay: number): AudioBuffer {
    const length = Math.max(1, Math.floor(ctx.sampleRate * seconds));
    const impulse = ctx.createBuffer(2, length, ctx.sampleRate);
    for (let channel = 0; channel < impulse.numberOfChannels; channel += 1) {
      const samples = impulse.getChannelData(channel);
      for (let i = 0; i < length; i += 1) {
        samples[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, decay);
      }
    }
    return impulse;
  }

  loadTrack(track: AudioItem) {
    this.ensureContext();
    this.resetBpmDetection();
    this.loopBeats = 0;
    this.loopStartMs = 0;
    this.loopEndMs = 0;
    this.currentTrack = track;
    this.fallbackIndex = 0;
    this.audioElement.src = track.uri;
    this.audioElement.load();
    this.enforcePitchPreservation();
    this.seekTo(0);
    this.applyPitchAndEffect();
    this.notify();
  }

  async play() {
    const context = this.ensureContext();
    if (context.state === 'suspended') {
      try {
        await context.resume();
      } catch (error) {
        console.warn('Could not resume DJ deck audio context:', error);
      }
    }
    this.enforcePitchPreservation();
    this.applyPitchAndEffect();
    try {
      await this.audioElement.play();
    } catch (error) {
      console.warn('Deck play was blocked or the track could not be decoded:', error);
    }
  }

  pause() {
    if (this.scratchActive) this.scratchWasPlaying = false;
    this.audioElement.pause();
  }

  /**
   * Begin a tactile vinyl drag. The track stays audible at a reduced level while
   * the platter scrubs the timeline; filtered vinyl friction supplies the
   * characteristic scratch texture rather than only changing the UI animation.
   */
  startScratch(): number {
    if (!this.currentTrack) return this.currentTimeMs;
    if (this.scratchActive) return this.currentTimeMs;

    const context = this.ensureContext();
    this.scratchWasPlaying = this.isPlaying;
    this.scratchActive = true;

    if (this.deckGain) {
      this.deckGain.gain.setTargetAtTime(this.volume * 0.32, context.currentTime, 0.008);
    }

    if (this.scratchNoiseBuffer && this.scratchFilter && !this.scratchNoiseSource) {
      const noiseSource = context.createBufferSource();
      noiseSource.buffer = this.scratchNoiseBuffer;
      noiseSource.loop = true;
      noiseSource.connect(this.scratchFilter);
      noiseSource.start();
      this.scratchNoiseSource = noiseSource;
    }

    this.scratchGain?.gain.setTargetAtTime(this.volume * 0.025, context.currentTime, 0.008);
    this.scratchOscillatorGain?.gain.setTargetAtTime(this.volume * 0.02, context.currentTime, 0.008);

    if (!this.isPlaying) void this.play();
    this.notify();
    return this.currentTimeMs;
  }

  /** Move the playback head with the record and shape the scratch sound from hand speed. */
  scratchTo(positionMs: number, velocityMsPerSecond: number): void {
    if (!this.scratchActive) return;
    const maxPositionMs = this.durationMs > 0 ? Math.max(0, this.durationMs - 35) : Number.MAX_SAFE_INTEGER;
    const targetMs = clamp(positionMs, 0, maxPositionMs);
    try {
      this.audioElement.currentTime = targetMs / 1000;
    } catch {
      // A newly imported track may not be seekable until its metadata arrives.
    }

    const context = this.ctx;
    const speed = Math.abs(Number.isFinite(velocityMsPerSecond) ? velocityMsPerSecond : 0);
    if (context) {
      const intensity = clamp(speed / 1650, 0, 1);
      this.scratchFilter?.frequency.setTargetAtTime(clamp(700 + speed * 0.58, 700, 4200), context.currentTime, 0.008);
      this.scratchGain?.gain.setTargetAtTime(this.volume * (0.025 + intensity * 0.22), context.currentTime, 0.006);
      this.scratchOscillator?.frequency.setTargetAtTime(clamp(82 + speed * 0.035, 82, 260), context.currentTime, 0.008);
      this.scratchOscillatorGain?.gain.setTargetAtTime(this.volume * (0.018 + intensity * 0.055), context.currentTime, 0.008);
    }

    // Negative media playback rates are not portable across browsers. Reverse
    // hand motion is therefore represented by reverse timeline seeks, while
    // the positive rate follows the speed of the hand.
    this.audioElement.playbackRate = clamp(Math.max(0.25, speed / 1000), 0.25, 2.5);
  }

  endScratch(): void {
    if (!this.scratchActive) return;
    const context = this.ctx;
    const shouldResume = this.scratchWasPlaying;
    this.scratchActive = false;

    if (context) {
      this.scratchGain?.gain.setTargetAtTime(0, context.currentTime, 0.018);
      this.scratchOscillatorGain?.gain.setTargetAtTime(0, context.currentTime, 0.018);
      this.deckGain?.gain.setTargetAtTime(this.volume, context.currentTime, 0.025);
      this.scratchNoiseSource?.stop();
      this.scratchNoiseSource?.disconnect();
      this.scratchNoiseSource = null;
    }
    this.applyPitchAndEffect();
    this.notify();
    if (!shouldResume) this.pause();
  }

  togglePlay() {
    if (this.isPlaying) this.pause();
    else void this.play();
  }

  cue() {
    this.seekTo(this.cuePositionMs);
    if (this.isPlaying) this.pause();
  }

  setCue() {
    this.cuePositionMs = this.currentTimeMs;
    this.notify();
  }

  /** Activate a beat-quantized loop, or pass 0 to turn it off. */
  setLoopBeats(beats: number): boolean {
    const next = Math.round(beats);
    if (next <= 0) {
      this.loopBeats = 0;
      this.loopStartMs = 0;
      this.loopEndMs = 0;
      this.notify();
      return true;
    }
    if (![1, 2, 4, 8, 16].includes(next) || !this.currentTrack) return false;

    // BPM measured from audio includes pitch. Convert beat length to media time.
    const playbackRate = Math.max(0.25, this.audioElement.playbackRate || 1);
    const measuredBpm = this.currentBpm || 120;
    const loopLengthMs = next * 60000 * playbackRate / measuredBpm;
    const trackLengthMs = this.durationMs;
    const safeTrackEndMs = trackLengthMs > 0 ? trackLengthMs - 120 : Number.POSITIVE_INFINITY;
    if (!Number.isFinite(loopLengthMs) || loopLengthMs <= 0 || loopLengthMs > safeTrackEndMs) return false;

    this.loopStartMs = Math.max(0, Math.min(this.currentTimeMs, safeTrackEndMs - loopLengthMs));
    this.loopEndMs = this.loopStartMs + loopLengthMs;
    this.loopBeats = next;
    this.notify();
    return true;
  }

  private handleActiveLoopFrame(): boolean {
    if (this.scratchActive || !this.isPlaying || this.loopBeats <= 0 || this.loopEndMs <= this.loopStartMs) return false;
    const currentMs = this.currentTimeMs;
    if (currentMs >= this.loopEndMs || currentMs < this.loopStartMs) {
      const lengthMs = this.loopEndMs - this.loopStartMs;
      const overshoot = currentMs >= this.loopEndMs ? (currentMs - this.loopStartMs) % lengthMs : 0;
      try {
        this.audioElement.currentTime = (this.loopStartMs + overshoot) / 1000;
      } catch {
        return false;
      }
      this.notify();
      return true;
    }
    return false;
  }

  onTrackEnded(callback: () => void): () => void {
    this.onTrackEndedCallbacks.push(callback);
    return () => {
      this.onTrackEndedCallbacks = this.onTrackEndedCallbacks.filter((registered) => registered !== callback);
    };
  }

  seekTo(positionMs: number) {
    if (!Number.isNaN(positionMs) && Number.isFinite(positionMs) && positionMs >= 0) {
      try {
        this.audioElement.currentTime = positionMs / 1000;
      } catch {
        // Seeking can be unavailable until a track has loaded.
      }
      this.notify();
    }
  }

  setPitch(pitchValue: number) {
    // Do not snap near 100%: the former +/-4% dead zone made the tempo control seem broken.
    this.pitch = clamp(pitchValue, 0.5, 1.5);
    this.applyPitchAndEffect();
    this.notify();
  }

  /**
   * Match this deck to a playing reference deck, then nudge this track's
   * timeline to the closest beat-grid phase. Beat phase is estimated from
   * the track's measured BPM and its current playback rate.
   */
  syncTo(reference: DJDeckEngine): {
    success: boolean;
    reason: 'synced' | 'pitch-limit' | 'unavailable' | 'not-playing';
  } {
    if (!this.isPlaying || !reference.isPlaying) {
      return { success: false, reason: 'not-playing' };
    }

    const ownBpm = this.currentBpm;
    const referenceBpm = reference.currentBpm;
    if (
      ownBpm === null ||
      referenceBpm === null ||
      this.bpmConfidence < 0.12 ||
      reference.bpmConfidence < 0.12
    ) {
      return { success: false, reason: 'unavailable' };
    }

    const previousPitch = this.pitch;
    const desiredPitch = previousPitch * (referenceBpm / ownBpm);
    const nextPitch = clamp(desiredPitch, 0.5, 1.5);
    const matchedBpm = ownBpm * (nextPitch / previousPitch);
    const pitchLimited = Math.abs(matchedBpm - referenceBpm) / referenceBpm > 0.015;

    // Convert detected output BPM back to track-timeline BPM. Playback rate
    // changes what the analyser hears, but not the time values in the media file.
    const ownPlaybackRate = Math.max(0.25, this.audioElement.playbackRate || 1);
    const referencePlaybackRate = Math.max(0.25, reference.audioElement.playbackRate || 1);
    const ownTrackBpm = ownBpm / ownPlaybackRate;
    const referenceTrackBpm = referenceBpm / referencePlaybackRate;

    if (
      !Number.isFinite(ownTrackBpm) ||
      !Number.isFinite(referenceTrackBpm) ||
      ownTrackBpm <= 0 ||
      referenceTrackBpm <= 0
    ) {
      return { success: false, reason: 'unavailable' };
    }

    const ownPhase = ((this.currentTimeMs * ownTrackBpm / 60000) % 1 + 1) % 1;
    const referencePhase = ((reference.currentTimeMs * referenceTrackBpm / 60000) % 1 + 1) % 1;
    let phaseShiftBeats = referencePhase - ownPhase;
    phaseShiftBeats = ((phaseShiftBeats + 0.5) % 1 + 1) % 1 - 0.5;

    const seekAdjustmentMs = phaseShiftBeats * 60000 / ownTrackBpm;
    const lastSafeMs = this.durationMs > 0 ? Math.max(0, this.durationMs - 50) : Number.MAX_SAFE_INTEGER;
    const alignedTimeMs = clamp(this.currentTimeMs + seekAdjustmentMs, 0, lastSafeMs);

    this.setPitch(nextPitch);
    this.seekTo(alignedTimeMs);

    return { success: true, reason: pitchLimited ? 'pitch-limit' : 'synced' };
  }

  setCrossfadeVolume(gain: number) {
    this.crossfadeVolume = clamp(gain, 0, 1);
    if (this.crossfadeGain && this.ctx) {
      this.crossfadeGain.gain.setTargetAtTime(this.crossfadeVolume, this.ctx.currentTime, 0.02);
    }
  }

  setDeckVolume(volume: number) {
    this.volume = clamp(volume, 0, 1);
    if (this.deckGain && this.ctx) {
      this.deckGain.gain.setTargetAtTime(this.scratchActive ? this.volume * 0.32 : this.volume, this.ctx.currentTime, 0.015);
    }
    this.notify();
  }

  setEffect(effect: DJEffectType) {
    this.ensureContext();
    this.activeEffect = effect;
    this.applyPitchAndEffect();
    this.notify();
  }

  setFxAmount(amount: number) {
    this.ensureContext();
    this.fxAmount = clamp(amount, 0, 1);
    this.applyPitchAndEffect();
    this.notify();
  }

  private applyPitchAndEffect() {
    this.enforcePitchPreservation();
    const amount = clamp(this.fxAmount, 0, 1);
    const voiceRate = VOICE_RATE[this.activeEffect];
    const effectiveRate = this.pitch * (voiceRate ? 1 + (voiceRate - 1) * amount : 1);
    if (!this.scratchActive) {
      this.audioElement.playbackRate = clamp(effectiveRate, 0.25, 2.5);
    }

    const ctx = this.ctx;
    if (!ctx) return;
    const now = ctx.currentTime;

    // 1. Robot & Alien ring modulators
    if (this.robotGain && this.robotModulatorGain && this.robotOscillator) {
      if (this.activeEffect === 'voice_robot') {
        this.robotOscillator.frequency.setTargetAtTime(140, now, 0.03);
        this.robotGain.gain.setTargetAtTime(amount * 0.85, now, 0.03);
        this.robotModulatorGain.gain.setTargetAtTime(1 - amount * 0.45, now, 0.03);
      } else if (this.activeEffect === 'voice_alien') {
        this.robotOscillator.frequency.setTargetAtTime(320, now, 0.03);
        this.robotGain.gain.setTargetAtTime(amount * 0.78, now, 0.03);
        this.robotModulatorGain.gain.setTargetAtTime(1 - amount * 0.35, now, 0.03);
      } else {
        this.robotGain.gain.setTargetAtTime(0, now, 0.03);
        this.robotModulatorGain.gain.setTargetAtTime(1, now, 0.03);
      }
    }

    // 2. Main EQ / Filter
    if (this.filterNode) {
      this.filterNode.type = 'allpass';
      this.filterNode.frequency.setTargetAtTime(1000, now, 0.03);
      this.filterNode.Q.setTargetAtTime(0.0001, now, 0.03);
      this.filterNode.gain.setTargetAtTime(0, now, 0.03);

      switch (this.activeEffect) {
        case 'fx_filter':
          if (amount < 0.48) {
            this.filterNode.type = 'lowpass';
            const norm = amount / 0.48;
            this.filterNode.frequency.setTargetAtTime(180 + Math.pow(norm, 2.4) * 19000, now, 0.03);
            this.filterNode.Q.setTargetAtTime(1.2 + (1 - norm) * 4, now, 0.03);
          } else if (amount > 0.52) {
            this.filterNode.type = 'highpass';
            const norm = (amount - 0.52) / 0.48;
            this.filterNode.frequency.setTargetAtTime(20 + Math.pow(norm, 2.2) * 6500, now, 0.03);
            this.filterNode.Q.setTargetAtTime(1.2 + norm * 4.5, now, 0.03);
          } else {
            this.filterNode.type = 'allpass';
            this.filterNode.frequency.setTargetAtTime(1000, now, 0.03);
            this.filterNode.Q.setTargetAtTime(0.0001, now, 0.03);
          }
          break;
        case 'fx_flanger':
          this.filterNode.type = 'allpass';
          this.filterNode.frequency.setTargetAtTime(800 + amount * 3500, now, 0.03);
          this.filterNode.Q.setTargetAtTime(0.5 + amount * 3, now, 0.03);
          break;
        case 'fx_phaser':
          this.filterNode.type = 'allpass';
          this.filterNode.frequency.setTargetAtTime(450 + amount * 4000, now, 0.03);
          this.filterNode.Q.setTargetAtTime(0.7 + amount * 6, now, 0.03);
          break;
        case 'fx_distortion':
          this.filterNode.type = 'peaking';
          this.filterNode.frequency.setTargetAtTime(1200, now, 0.03);
          this.filterNode.Q.setTargetAtTime(1.2, now, 0.03);
          this.filterNode.gain.setTargetAtTime(amount * 9, now, 0.03);
          break;
        case 'fx_bitcrush':
          this.filterNode.type = 'lowpass';
          this.filterNode.frequency.setTargetAtTime(700 + (1 - amount) * 5500, now, 0.03);
          this.filterNode.Q.setTargetAtTime(1.2, now, 0.03);
          break;
        case 'voice_robot':
          this.filterNode.type = 'peaking';
          this.filterNode.frequency.setTargetAtTime(1500, now, 0.03);
          this.filterNode.Q.setTargetAtTime(2.5, now, 0.03);
          this.filterNode.gain.setTargetAtTime(amount * 8, now, 0.03);
          break;
        case 'voice_alien':
          this.filterNode.type = 'peaking';
          this.filterNode.frequency.setTargetAtTime(2200, now, 0.03);
          this.filterNode.Q.setTargetAtTime(4.0, now, 0.03);
          this.filterNode.gain.setTargetAtTime(amount * 9, now, 0.03);
          break;
        case 'voice_chipmunk':
          this.filterNode.type = 'highpass';
          this.filterNode.frequency.setTargetAtTime(360 + amount * 240, now, 0.03);
          this.filterNode.Q.setTargetAtTime(1.5, now, 0.03);
          break;
        case 'voice_monster':
          this.filterNode.type = 'lowshelf';
          this.filterNode.frequency.setTargetAtTime(140, now, 0.03);
          this.filterNode.gain.setTargetAtTime(8 + amount * 12, now, 0.03);
          break;
        case 'voice_demon':
          this.filterNode.type = 'lowshelf';
          this.filterNode.frequency.setTargetAtTime(110, now, 0.03);
          this.filterNode.gain.setTargetAtTime(10 + amount * 14, now, 0.03);
          break;
        case 'voice_giant':
          this.filterNode.type = 'lowshelf';
          this.filterNode.frequency.setTargetAtTime(75, now, 0.03);
          this.filterNode.gain.setTargetAtTime(12 + amount * 15, now, 0.03);
          break;
        case 'voice_kid':
          this.filterNode.type = 'peaking';
          this.filterNode.frequency.setTargetAtTime(1800, now, 0.03);
          this.filterNode.Q.setTargetAtTime(1.8, now, 0.03);
          this.filterNode.gain.setTargetAtTime(5 + amount * 8, now, 0.03);
          break;
        case 'voice_woman':
          this.filterNode.type = 'peaking';
          this.filterNode.frequency.setTargetAtTime(2400, now, 0.03);
          this.filterNode.Q.setTargetAtTime(1.4, now, 0.03);
          this.filterNode.gain.setTargetAtTime(4 + amount * 7, now, 0.03);
          break;
        case 'voice_telephone':
          this.filterNode.type = 'highpass';
          this.filterNode.frequency.setTargetAtTime(480, now, 0.03);
          this.filterNode.Q.setTargetAtTime(1.2, now, 0.03);
          break;
        case 'voice_radio':
          this.filterNode.type = 'bandpass';
          this.filterNode.frequency.setTargetAtTime(1800, now, 0.03);
          this.filterNode.Q.setTargetAtTime(1.6, now, 0.03);
          break;
        default:
          break;
      }
    }

    // 3. Voice Formant Shaping Filter
    if (this.voiceFormantFilter) {
      this.voiceFormantFilter.type = 'allpass';
      this.voiceFormantFilter.frequency.setTargetAtTime(1000, now, 0.03);
      this.voiceFormantFilter.gain.setTargetAtTime(0, now, 0.03);

      switch (this.activeEffect) {
        case 'voice_chipmunk':
          this.voiceFormantFilter.type = 'peaking';
          this.voiceFormantFilter.frequency.setTargetAtTime(2800, now, 0.03);
          this.voiceFormantFilter.Q.setTargetAtTime(2.2, now, 0.03);
          this.voiceFormantFilter.gain.setTargetAtTime(5 + amount * 9, now, 0.03);
          break;
        case 'voice_robot':
          this.voiceFormantFilter.type = 'highpass';
          this.voiceFormantFilter.frequency.setTargetAtTime(260, now, 0.03);
          break;
        case 'voice_monster':
          this.voiceFormantFilter.type = 'lowpass';
          this.voiceFormantFilter.frequency.setTargetAtTime(4500, now, 0.03);
          break;
        case 'voice_giant':
          this.voiceFormantFilter.type = 'lowpass';
          this.voiceFormantFilter.frequency.setTargetAtTime(3200, now, 0.03);
          break;
        case 'voice_kid':
          this.voiceFormantFilter.type = 'highpass';
          this.voiceFormantFilter.frequency.setTargetAtTime(220, now, 0.03);
          break;
        case 'voice_woman':
          this.voiceFormantFilter.type = 'highshelf';
          this.voiceFormantFilter.frequency.setTargetAtTime(5000, now, 0.03);
          this.voiceFormantFilter.gain.setTargetAtTime(2 + amount * 5, now, 0.03);
          break;
        case 'voice_telephone':
          this.voiceFormantFilter.type = 'lowpass';
          this.voiceFormantFilter.frequency.setTargetAtTime(2800, now, 0.03);
          this.voiceFormantFilter.Q.setTargetAtTime(1.2, now, 0.03);
          break;
        default:
          break;
      }
    }

    // 4. WaveShaper distortion / saturation
    if (this.waveshaperNode) {
      let distortion = 0;
      if (this.activeEffect === 'fx_distortion') distortion = 20 + amount * 480;
      else if (this.activeEffect === 'fx_bitcrush') distortion = 60 + amount * 300;
      else if (this.activeEffect === 'voice_monster') distortion = 35 + amount * 120;
      else if (this.activeEffect === 'voice_demon') distortion = 40 + amount * 160;
      else if (this.activeEffect === 'voice_giant') distortion = amount * 60;
      else if (this.activeEffect === 'voice_telephone') distortion = 25 + amount * 35;
      else if (this.activeEffect === 'voice_radio') distortion = 30 + amount * 40;
      this.waveshaperNode.curve = this.makeDistortionCurve(distortion);
    }

    if (this.compressorNode) {
      const active = this.activeEffect === 'fx_compressor';
      this.compressorNode.threshold.setTargetAtTime(active ? -24 - amount * 30 : -18, now, 0.03);
      this.compressorNode.knee.setTargetAtTime(active ? 6 : 20, now, 0.03);
      this.compressorNode.ratio.setTargetAtTime(active ? 4 + amount * 14 : 3, now, 0.03);
      this.compressorNode.attack.setTargetAtTime(active ? 0.002 : 0.015, now, 0.03);
      this.compressorNode.release.setTargetAtTime(active ? 0.08 + amount * 0.3 : 0.25, now, 0.03);
    }

    if (this.delayNode && this.delayFeedback && this.delayWetGain && this.delayLfo && this.delayLfoGain) {
      const isDelay = this.activeEffect === 'fx_delay';
      const isFlanger = this.activeEffect === 'fx_flanger';
      const isPhaser = this.activeEffect === 'fx_phaser';
      const isDemon = this.activeEffect === 'voice_demon';

      // Always guarantee delayBase > lfoDepth so delayTime NEVER becomes negative
      const delayBase = isDelay ? 0.16 + amount * 0.42
        : isFlanger ? 0.015
        : isPhaser ? 0.020
        : isDemon ? 0.035
        : 0.12;
      const wet = isDelay ? amount * (0.65 + amount * 0.45)
        : isFlanger ? amount * 0.82
        : isPhaser ? amount * 0.75
        : isDemon ? amount * 0.42
        : 0;
      const feedback = isDelay ? 0.25 + amount * 0.55
        : isFlanger ? 0.35 + amount * 0.35
        : isPhaser ? 0.25 + amount * 0.25
        : isDemon ? 0.3
        : 0;
      const lfoFreq = isFlanger ? 0.2 + amount * 3.2
        : isPhaser ? 0.15 + amount * 1.5
        : 0.25;
      const lfoDepth = isFlanger ? 0.002 + amount * 0.005
        : isPhaser ? 0.001 + amount * 0.003
        : 0;

      this.delayNode.delayTime.setTargetAtTime(delayBase, now, 0.025);
      this.delayWetGain.gain.setTargetAtTime(wet, now, 0.025);
      this.delayFeedback.gain.setTargetAtTime(feedback, now, 0.025);
      this.delayLfo.frequency.setTargetAtTime(lfoFreq, now, 0.025);
      this.delayLfoGain.gain.setTargetAtTime(lfoDepth, now, 0.025);
    }

    if (this.reverbWetGain) {
      this.reverbWetGain.gain.setTargetAtTime(this.activeEffect === 'fx_reverb' ? amount * 1.25 : 0, now, 0.025);
    }
  }

  private makeDistortionCurve(amount: number): Float32Array<ArrayBuffer> {
    const sampleCount = 44100;
    const curve = new Float32Array(new ArrayBuffer(sampleCount * 4));
    const k = Math.max(0, amount);
    const deg = Math.PI / 180;
    for (let i = 0; i < sampleCount; i += 1) {
      const x = (i * 2) / sampleCount - 1;
      curve[i] = k === 0
        ? x
        : ((3 + k) * x * 20 * deg) / (Math.PI + k * Math.abs(x));
    }
    return curve;
  }


  private resetBpmDetection(): void {
    this.stopBpmDetection();
    this.currentBpm = null;
    this.bpmConfidence = 0;
    this.bpmSamples = [];
    this.bpmCandidate = null;
    this.bpmCandidateHits = 0;
    this.bpmLastSampleAt = 0;
    this.bpmLastEvaluationAt = 0;
    this.bpmSampleIntervalMs = 50;
    if (this.bpmPreviousSpectrum.length !== (this.bpmAnalyser?.frequencyBinCount || 0)) {
      this.bpmPreviousSpectrum = new Uint8Array(this.bpmAnalyser?.frequencyBinCount || 0);
    } else {
      this.bpmPreviousSpectrum.fill(0);
    }
  }

  private startBpmDetection(): void {
    if (!this.bpmAnalyser || this.bpmFrameId !== null || !this.currentTrack) return;
    this.bpmLastSampleAt = 0;
    this.bpmFrameId = window.requestAnimationFrame(this.sampleBpmFrame);
  }

  private stopBpmDetection(): void {
    if (this.bpmFrameId !== null) {
      window.cancelAnimationFrame(this.bpmFrameId);
      this.bpmFrameId = null;
    }
    this.bpmLastSampleAt = 0;
  }

  /**
   * Real-time beat tracking using positive spectral flux and an onset-envelope
   * autocorrelation. It needs no song metadata and updates once per second after
   * enough live audio has been observed.
   */
  private sampleBpmFrame = (timestamp: number): void => {
    this.bpmFrameId = null;
    if (!this.isPlaying || !this.bpmAnalyser || !this.ctx) return;

    if (this.handleActiveLoopFrame()) {
      this.bpmLastSampleAt = timestamp;
      this.bpmFrameId = window.requestAnimationFrame(this.sampleBpmFrame);
      return;
    }

    if (this.bpmLastSampleAt === 0) {
      this.bpmLastSampleAt = timestamp;
      this.bpmFrameId = window.requestAnimationFrame(this.sampleBpmFrame);
      return;
    }

    const elapsedMs = timestamp - this.bpmLastSampleAt;
    if (elapsedMs < 45) {
      this.bpmFrameId = window.requestAnimationFrame(this.sampleBpmFrame);
      return;
    }

    this.bpmLastSampleAt = timestamp;
    this.bpmSampleIntervalMs = this.bpmSamples.length === 0
      ? elapsedMs
      : this.bpmSampleIntervalMs * 0.8 + elapsedMs * 0.2;

    const spectrum = new Uint8Array(this.bpmAnalyser.frequencyBinCount);
    this.bpmAnalyser.getByteFrequencyData(spectrum);

    // Positive spectral flux emphasizes new percussive energy instead of the
    // sustained tonal energy that otherwise dominates a music spectrum.
    const firstBin = 3;
    const lastBin = Math.min(
      spectrum.length - 1,
      Math.floor(2400 * this.bpmAnalyser.fftSize / (this.ctx.sampleRate || 44100)),
    );
    let flux = 0;
    let activeBins = 0;
    for (let bin = firstBin; bin <= lastBin; bin += 1) {
      const increase = spectrum[bin] - (this.bpmPreviousSpectrum[bin] || 0);
      if (increase > 0) flux += increase;
      activeBins += 1;
    }
    this.bpmPreviousSpectrum = spectrum;
    this.bpmSamples.push(activeBins > 0 ? flux / activeBins : 0);

    // Keep around 16 seconds for a stable tempo estimate without unbounded memory.
    if (this.bpmSamples.length > 320) this.bpmSamples.shift();

    if (
      this.bpmSamples.length >= 100 &&
      (this.bpmLastEvaluationAt === 0 || timestamp - this.bpmLastEvaluationAt >= 1000)
    ) {
      this.bpmLastEvaluationAt = timestamp;
      this.estimateBpmFromOnsets();
    }

    this.bpmFrameId = window.requestAnimationFrame(this.sampleBpmFrame);
  };

  private estimateBpmFromOnsets(): void {
    const samples = this.bpmSamples;
    if (samples.length < 100) return;

    const mean = samples.reduce((total, value) => total + value, 0) / samples.length;
    let variance = 0;
    for (const value of samples) variance += (value - mean) ** 2;
    variance /= samples.length;

    // Silence or near-constant ambience has no reliable beat envelope.
    if (variance < 0.08) {
      this.bpmConfidence = 0;
      this.notify();
      return;
    }

    const intervalMs = Math.max(40, Math.min(90, this.bpmSampleIntervalMs));
    const minLag = Math.max(2, Math.floor(60000 / (200 * intervalMs)));
    const maxLag = Math.min(samples.length >> 1, Math.ceil(60000 / (60 * intervalMs)));
    let bestLag = 0;
    let bestCorrelation = -1;

    for (let lag = minLag; lag <= maxLag; lag += 1) {
      let dot = 0;
      let leftEnergy = 0;
      let rightEnergy = 0;
      for (let index = lag; index < samples.length; index += 1) {
        const left = samples[index] - mean;
        const right = samples[index - lag] - mean;
        dot += left * right;
        leftEnergy += left * left;
        rightEnergy += right * right;
      }
      const denominator = Math.sqrt(leftEnergy * rightEnergy);
      const correlation = denominator > 0 ? dot / denominator : 0;
      if (correlation > bestCorrelation) {
        bestCorrelation = correlation;
        bestLag = lag;
      }
    }

    this.bpmConfidence = Math.max(0, bestCorrelation);
    if (bestLag === 0 || bestCorrelation < 0.12) {
      this.notify();
      return;
    }

    const detectedBpm = Math.round(60000 / (bestLag * intervalMs));
    if (!Number.isFinite(detectedBpm) || detectedBpm < 60 || detectedBpm > 200) {
      this.notify();
      return;
    }

    if (this.currentBpm === null) {
      this.currentBpm = detectedBpm;
      this.bpmCandidate = null;
      this.bpmCandidateHits = 0;
    } else if (Math.abs(detectedBpm - this.currentBpm) <= 4) {
      // Smooth small estimate fluctuations, so the deck display doesn't flicker.
      this.currentBpm = Math.round(this.currentBpm * 0.65 + detectedBpm * 0.35);
      this.bpmCandidate = null;
      this.bpmCandidateHits = 0;
    } else {
      if (this.bpmCandidate !== null && Math.abs(detectedBpm - this.bpmCandidate) <= 3) {
        this.bpmCandidate = Math.round(this.bpmCandidate * 0.5 + detectedBpm * 0.5);
        this.bpmCandidateHits += 1;
      } else {
        this.bpmCandidate = detectedBpm;
        this.bpmCandidateHits = 1;
      }
      // Require repeated evidence before replacing a stable reading.
      if (this.bpmCandidateHits >= 3) {
        this.currentBpm = this.bpmCandidate;
        this.bpmCandidate = null;
        this.bpmCandidateHits = 0;
      }
    }

    this.notify();
  }

  get currentTimeMs(): number {
    return (this.audioElement.currentTime || 0) * 1000;
  }

  get durationMs(): number {
    return (this.audioElement.duration || 0) * 1000;
  }

  getVisualizerData(): Uint8Array {
    if (!this.analyser) return new Uint8Array(128);
    const data = new Uint8Array(this.analyser.frequencyBinCount);
    this.analyser.getByteFrequencyData(data);
    return data;
  }

  subscribe(callback: () => void): () => void {
    this.onUpdateCallbacks.push(callback);
    return () => {
      this.onUpdateCallbacks = this.onUpdateCallbacks.filter((registered) => registered !== callback);
    };
  }

  private notify() {
    this.onUpdateCallbacks.forEach((callback) => {
      try {
        callback();
      } catch (error) {
        console.warn('DJ deck subscriber failed:', error);
      }
    });
  }
}

export const deckAEngine = new DJDeckEngine('DECK A');
export const deckBEngine = new DJDeckEngine('DECK B');
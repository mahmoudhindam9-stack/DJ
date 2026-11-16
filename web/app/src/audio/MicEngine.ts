import { MicFilterType, BeatFxDivision, MicVoiceEffectType } from '../types';
import { connectGlobalEqualizer, GlobalEqualizerConnection } from './GlobalEqualizer';

export class MicEngine {
  private ctx: AudioContext | null = null;
  private micStream: MediaStream | null = null;
  private micSource: MediaStreamAudioSourceNode | null = null;

  // Nodes
  private inputGain: GainNode | null = null;
  private voiceWorkletNode: AudioWorkletNode | null = null;
  private filterDryGain: GainNode | null = null;
  private filterWetGain: GainNode | null = null;
  private filterNode: BiquadFilterNode | null = null;
  private echoDelay: DelayNode | null = null;
  private beatDelay: DelayNode | null = null;
  private beatFeedback: GainNode | null = null;
  private beatWetGain: GainNode | null = null;
  private echoFeedback: GainNode | null = null;
  private echoWetGain: GainNode | null = null;
  private reverbConvolver: ConvolverNode | null = null;
  private reverbWetGain: GainNode | null = null;
  private flangerDelay: DelayNode | null = null;
  private flangerWetGain: GainNode | null = null;
  private flangerLfo: OscillatorNode | null = null;
  private flangerLfoGain: GainNode | null = null;
  private masterOutputGain: GainNode | null = null;
  private recordingDestination: MediaStreamAudioDestinationNode | null = null;
  private globalEqConnection: GlobalEqualizerConnection | null = null;
  private analyser: AnalyserNode | null = null;

  // MediaRecorder for recording processed vocals
  private recorder: MediaRecorder | null = null;
  private recordedChunks: Blob[] = [];
  private recordingTimer: number | null = null;

  // State
  public isMicEnabled = false;
  public isRecording = false;
  public recordingSeconds = 0;
  public micVolume = 1.2; // 0 to 2.0, same range as Android
  public echoLevel = 0.3; // 0 to 1.0
  public reverbLevel = 0.28; // 0 to 1.0
  public flangerMix = 0.35; // 0 to 1.0
  public filterMix = 0.55; // 0 to 1.0
  public currentFilter: MicFilterType = 'STUDIO_REVERB';
  public currentVoiceEffect: MicVoiceEffectType = 'NONE';
  public beatFxEnabled = true;
  public bpm = 120;
  public beatDivision: BeatFxDivision = '1/4';
  public voiceProcessing = true; // AEC & noise suppression
  public inputDevices: MediaDeviceInfo[] = [];
  public selectedDeviceId: string | null = null;

  private onUpdateCallbacks: Array<() => void> = [];

  constructor() {
    this.refreshDevices();
  }

  async refreshDevices(): Promise<MediaDeviceInfo[]> {
    try {
      if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
        const devices = await navigator.mediaDevices.enumerateDevices();
        this.inputDevices = devices.filter((d) => d.kind === 'audioinput');
        this.notify();
      }
    } catch (e) {
      console.warn('Device enumeration note:', e);
    }
    return this.inputDevices;
  }

  async toggleMic(enable?: boolean): Promise<boolean> {
    const targetState = enable !== undefined ? enable : !this.isMicEnabled;
    if (targetState === this.isMicEnabled) return this.isMicEnabled;

    if (targetState) {
      try {
        const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        this.ctx = new AudioCtx();
        if (this.ctx.state === 'suspended') {
          await this.ctx.resume();
        }

        const constraints: MediaStreamConstraints = {
          audio: {
            deviceId: this.selectedDeviceId ? { exact: this.selectedDeviceId } : undefined,
            echoCancellation: this.voiceProcessing,
            noiseSuppression: this.voiceProcessing,
            autoGainControl: false,
          },
        };

        this.micStream = await navigator.mediaDevices.getUserMedia(constraints);
        await this.setupAudioChain(this.ctx, this.micStream);
        this.isMicEnabled = true;
        this.notify();
        return true;
      } catch (err) {
        console.error('Failed to open microphone:', err);
        this.isMicEnabled = false;
        this.notify();
        return false;
      }
    } else {
      this.cleanupMic();
      this.isMicEnabled = false;
      this.notify();
      return false;
    }
  }

  private async setupAudioChain(ctx: AudioContext, stream: MediaStream) {
    this.micSource = ctx.createMediaStreamSource(stream);
    this.inputGain = ctx.createGain();
    this.inputGain.gain.value = this.micVolume;

    // Android parity: character voices run in an AudioWorklet off the UI thread.
    try {
      if (ctx.audioWorklet && typeof AudioWorkletNode !== 'undefined') {
        await ctx.audioWorklet.addModule(new URL('/mic-voice-worklet.js', window.location.href).toString());
        this.voiceWorkletNode = new AudioWorkletNode(ctx, 'dj-mic-voice-processor', {
          numberOfInputs: 1,
          numberOfOutputs: 1,
          outputChannelCount: [1],
          channelCount: 1,
          channelCountMode: 'explicit',
        });
        this.syncVoiceProcessor();
      }
    } catch (error) {
      this.voiceWorkletNode = null;
      console.warn('Mic character processor unavailable; using standard Web Audio filters:', error);
    }

    this.filterNode = ctx.createBiquadFilter();
    this.applyVocalFilter();
    this.filterDryGain = ctx.createGain();
    this.filterWetGain = ctx.createGain();
    this.filterDryGain.gain.value = 1 - this.filterMix;
    this.filterWetGain.gain.value = this.filterMix;

    this.echoDelay = ctx.createDelay(2.0);
    this.echoDelay.delayTime.value = 0.24;
    this.echoFeedback = ctx.createGain();
    this.echoFeedback.gain.value = 0.42;
    this.echoWetGain = ctx.createGain();
    this.echoWetGain.gain.value = this.echoLevel;
    this.echoDelay.connect(this.echoFeedback);
    this.echoFeedback.connect(this.echoDelay);
    this.echoDelay.connect(this.echoWetGain);

    this.reverbConvolver = ctx.createConvolver();
    this.reverbConvolver.buffer = this.buildReverbImpulse(ctx, 2.8, 2.35);
    this.reverbWetGain = ctx.createGain();
    this.reverbWetGain.gain.value = this.reverbLevel;
    this.reverbConvolver.connect(this.reverbWetGain);

    this.beatDelay = ctx.createDelay(2.0);
    this.beatFeedback = ctx.createGain();
    this.beatFeedback.gain.value = 0.28;
    this.beatWetGain = ctx.createGain();
    this.beatWetGain.gain.value = this.beatFxEnabled ? 0.28 : 0;
    this.beatDelay.connect(this.beatFeedback);
    this.beatFeedback.connect(this.beatDelay);
    this.beatDelay.connect(this.beatWetGain);

    this.flangerDelay = ctx.createDelay(0.05);
    this.flangerDelay.delayTime.value = 0.006;
    this.flangerLfo = ctx.createOscillator();
    this.flangerLfo.frequency.value = 0.35;
    this.flangerLfoGain = ctx.createGain();
    this.flangerLfoGain.gain.value = this.flangerMix * 0.006;
    this.flangerLfo.connect(this.flangerLfoGain);
    this.flangerLfoGain.connect(this.flangerDelay.delayTime);
    this.flangerLfo.start();
    this.flangerWetGain = ctx.createGain();
    this.flangerWetGain.gain.value = this.flangerMix * 0.72;
    this.flangerDelay.connect(this.flangerWetGain);

    this.masterOutputGain = ctx.createGain();
    this.masterOutputGain.gain.value = 1.0;
    this.analyser = ctx.createAnalyser();
    this.analyser.fftSize = 128;
    this.recordingDestination = ctx.createMediaStreamDestination();

    this.micSource.connect(this.inputGain);
    const voiceOutput: AudioNode = this.voiceWorkletNode || this.inputGain;
    if (this.voiceWorkletNode) this.inputGain.connect(this.voiceWorkletNode);
    voiceOutput.connect(this.filterDryGain);
    voiceOutput.connect(this.filterNode);
    this.filterNode.connect(this.filterWetGain);
    this.filterDryGain.connect(this.masterOutputGain);
    this.filterWetGain.connect(this.masterOutputGain);

    this.filterNode.connect(this.echoDelay);
    this.filterNode.connect(this.reverbConvolver);
    this.filterNode.connect(this.flangerDelay);
    this.filterNode.connect(this.beatDelay);

    this.echoWetGain.connect(this.masterOutputGain);
    this.reverbWetGain.connect(this.masterOutputGain);
    this.flangerWetGain.connect(this.masterOutputGain);
    this.beatWetGain.connect(this.masterOutputGain);

    this.globalEqConnection = connectGlobalEqualizer(ctx, this.masterOutputGain, this.analyser);
    this.analyser.connect(ctx.destination);
    // Recording receives the post-EQ signal too, matching the live output.
    this.analyser.connect(this.recordingDestination);
    this.setDelayTimeFromBpm();
    this.setFilterMix(this.filterMix);
    this.setFlangerMix(this.flangerMix);
    this.setEchoLevel(this.echoLevel);
    this.setReverbLevel(this.reverbLevel);
    this.setBeatFxEnabled(this.beatFxEnabled);
  }

  private buildReverbImpulse(ctx: AudioContext, duration: number, decay: number): AudioBuffer {
    const rate = ctx.sampleRate;
    const length = rate * duration;
    const impulse = ctx.createBuffer(2, length, rate);
    const left = impulse.getChannelData(0);
    const right = impulse.getChannelData(1);

    for (let i = 0; i < length; i++) {
      const n = (Math.random() * 2 - 1) * Math.pow(1 - i / length, decay);
      left[i] = n;
      right[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, decay);
    }
    return impulse;
  }

  private setDelayTimeFromBpm() {
    if (!this.beatDelay || !this.ctx) return;
    const beatSeconds = 60 / this.bpm;
    const multiplierByDivision: Record<BeatFxDivision, number> = {
      '1/1': 1, '1/2': 0.5, '1/4': 0.25, '1/8': 0.125, '3/4': 0.75,
    };
    const delayTime = Math.max(0.05, Math.min(1.8, beatSeconds * multiplierByDivision[this.beatDivision]));
    this.beatDelay.delayTime.setTargetAtTime(delayTime, this.ctx.currentTime, 0.05);
  }

  private syncVoiceProcessor(): void {
    this.voiceWorkletNode?.port.postMessage({
      voiceEffect: this.currentVoiceEffect,
      currentFilter: this.currentFilter,
    });
  }

  public applyVocalFilter() {
    this.syncVoiceProcessor();
    if (!this.filterNode || !this.ctx) return;
    const now = this.ctx.currentTime;
    switch (this.currentFilter) {
      case 'WARM':
        this.filterNode.type = 'lowshelf';
        this.filterNode.frequency.setTargetAtTime(300, now, 0.05);
        this.filterNode.gain.setTargetAtTime(5, now, 0.05);
        break;
      case 'BRIGHT':
        this.filterNode.type = 'highshelf';
        this.filterNode.frequency.setTargetAtTime(3500, now, 0.05);
        this.filterNode.gain.setTargetAtTime(6, now, 0.05);
        break;
      case 'TELEPHONE':
        this.filterNode.type = 'bandpass';
        this.filterNode.frequency.setTargetAtTime(1400, now, 0.05);
        this.filterNode.Q.setTargetAtTime(2.5, now, 0.05);
        break;
      case 'ROBOT':
        this.filterNode.type = 'peaking';
        this.filterNode.frequency.setTargetAtTime(2200, now, 0.05);
        this.filterNode.Q.setTargetAtTime(2.2, now, 0.05);
        this.filterNode.gain.setTargetAtTime(5, now, 0.05);
        break;
      case 'RADIO':
        this.filterNode.type = 'bandpass';
        this.filterNode.frequency.setTargetAtTime(1800, now, 0.05);
        this.filterNode.Q.setTargetAtTime(1.1, now, 0.05);
        break;
      case 'MEGAPHONE':
        this.filterNode.type = 'highpass';
        this.filterNode.frequency.setTargetAtTime(650, now, 0.05);
        break;
      case 'CLUB':
        this.filterNode.type = 'lowshelf';
        this.filterNode.frequency.setTargetAtTime(120, now, 0.05);
        this.filterNode.gain.setTargetAtTime(6, now, 0.05);
        break;
      case 'KID':
      case 'CHIPMUNK':
      case 'SMALL_WOMAN':
      case 'OLD_WOMAN':
      case 'OLD_MAN':
      case 'GIANT':
      case 'MONSTER':
      case 'NORMAL':
      case 'STUDIO_REVERB':
      case 'CHORUS':
      case 'TREMOLO':
      case 'BASS_BOOST':
      case 'NONE':
      default:
        this.filterNode.type = 'allpass';
        this.filterNode.frequency.setTargetAtTime(1000, now, 0.05);
        this.filterNode.Q.setTargetAtTime(0.0001, now, 0.05);
        this.filterNode.gain.setTargetAtTime(0, now, 0.05);
        break;
    }
  }

  setMicVolume(vol: number) {
    this.micVolume = Math.max(0, Math.min(2.0, vol));
    if (this.inputGain && this.ctx) {
      this.inputGain.gain.setTargetAtTime(this.micVolume, this.ctx.currentTime, 0.02);
    }
    this.notify();
  }

  setEchoLevel(level: number) {
    this.echoLevel = Math.max(0, Math.min(1.0, level));
    if (this.echoWetGain && this.ctx) {
      this.echoWetGain.gain.setTargetAtTime(this.echoLevel, this.ctx.currentTime, 0.02);
    }
    this.notify();
  }

  setReverbLevel(level: number) {
    this.reverbLevel = Math.max(0, Math.min(1.0, level));
    if (this.reverbWetGain && this.ctx) {
      this.reverbWetGain.gain.setTargetAtTime(this.reverbLevel, this.ctx.currentTime, 0.02);
    }
    this.notify();
  }

  setFlangerMix(mix: number) {
    this.flangerMix = Math.max(0, Math.min(1.0, mix));
    if (this.flangerWetGain && this.ctx) {
      this.flangerWetGain.gain.setTargetAtTime(this.flangerMix * 0.72, this.ctx.currentTime, 0.02);
    }
    if (this.flangerLfoGain && this.ctx) {
      this.flangerLfoGain.gain.setTargetAtTime(this.flangerMix * 0.006, this.ctx.currentTime, 0.02);
    }
    this.notify();
  }

  setFilterMix(mix: number) {
    this.filterMix = Math.max(0, Math.min(1, mix));
    if (this.filterDryGain && this.filterWetGain && this.ctx) {
      const now = this.ctx.currentTime;
      this.filterDryGain.gain.setTargetAtTime(1 - this.filterMix, now, 0.02);
      this.filterWetGain.gain.setTargetAtTime(this.filterMix, now, 0.02);
    }
    this.syncVoiceProcessor();
    this.notify();
  }

  setVoiceEffect(effect: MicVoiceEffectType) {
    this.currentVoiceEffect = effect;
    this.syncVoiceProcessor();
    this.notify();
  }

  setBeatFxEnabled(enabled: boolean) {
    this.beatFxEnabled = enabled;
    if (this.beatWetGain && this.ctx) {
      this.beatWetGain.gain.setTargetAtTime(enabled ? 0.28 : 0, this.ctx.currentTime, 0.02);
    }
    this.notify();
  }

  setFilter(filter: MicFilterType) {
    this.currentFilter = filter;
    this.applyVocalFilter();
    this.notify();
  }

  setBpm(bpm: number) {
    this.bpm = Math.max(70, Math.min(180, bpm));
    this.setDelayTimeFromBpm();
    this.notify();
  }

  setBeatDivision(div: BeatFxDivision) {
    this.beatDivision = div;
    this.setDelayTimeFromBpm();
    this.notify();
  }

  toggleVoiceProcessing(enable: boolean) {
    this.voiceProcessing = enable;
    if (this.isMicEnabled) {
      // reinit stream
      this.toggleMic(false).then(() => this.toggleMic(true));
    }
    this.notify();
  }

  selectDevice(deviceId: string | null) {
    this.selectedDeviceId = deviceId;
    if (this.isMicEnabled) {
      this.toggleMic(false).then(() => this.toggleMic(true));
    }
    this.notify();
  }

  // --- RECORDING ---
  startRecording(): boolean {
    if (!this.micStream) return false;
    try {
      this.recordedChunks = [];
      const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : 'audio/webm';

      // Record the post-effects signal, not the raw microphone capture.
      const recordingStream = this.recordingDestination?.stream || this.micStream;
      this.recorder = new MediaRecorder(recordingStream, { mimeType });
      this.recorder.ondataavailable = (e) => {
        if (e.data.size > 0) this.recordedChunks.push(e.data);
      };
      this.recorder.start(250);
      this.isRecording = true;
      this.recordingSeconds = 0;

      if (this.recordingTimer) clearInterval(this.recordingTimer);
      this.recordingTimer = window.setInterval(() => {
        this.recordingSeconds++;
        this.notify();
      }, 1000);

      this.notify();
      return true;
    } catch (e) {
      console.error('Failed to start recording:', e);
      return false;
    }
  }

  stopRecordingAndDownload(filenamePrefix = 'Karaoke_Recording'): void {
    if (!this.recorder || !this.isRecording) return;

    if (this.recordingTimer) {
      clearInterval(this.recordingTimer);
      this.recordingTimer = null;
    }

    this.recorder.onstop = () => {
      const blob = new Blob(this.recordedChunks, { type: 'audio/webm' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      a.href = url;
      a.download = `${filenamePrefix}_${timestamp}.webm`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(() => URL.revokeObjectURL(url), 5000);
      this.isRecording = false;
      this.recordingSeconds = 0;
      this.notify();
    };

    this.recorder.stop();
  }

  getVuLevel(): number {
    if (!this.analyser) return 0;
    const data = new Uint8Array(this.analyser.frequencyBinCount);
    this.analyser.getByteFrequencyData(data);
    let sum = 0;
    for (let i = 0; i < data.length; i++) {
      sum += data[i];
    }
    return Math.min(100, Math.round((sum / (data.length * 255)) * 100 * 1.5));
  }

  private cleanupMic() {
    if (this.isRecording) {
      this.stopRecordingAndDownload();
    }
    if (this.recordingTimer) {
      clearInterval(this.recordingTimer);
      this.recordingTimer = null;
    }
    if (this.micStream) {
      this.micStream.getTracks().forEach((track) => track.stop());
      this.micStream = null;
    }
    this.globalEqConnection?.dispose();
    this.globalEqConnection = null;
    if (this.ctx && this.ctx.state !== 'closed') {
      this.ctx.close().catch(() => {});
      this.ctx = null;
    }
    this.micSource = null;
    this.inputGain = null;
    this.voiceWorkletNode = null;
    this.filterNode = null;
    this.filterDryGain = null;
    this.filterWetGain = null;
    this.echoDelay = null;
    this.echoFeedback = null;
    this.echoWetGain = null;
    this.beatDelay = null;
    this.beatFeedback = null;
    this.beatWetGain = null;
    this.reverbConvolver = null;
    this.reverbWetGain = null;
    this.flangerDelay = null;
    this.flangerWetGain = null;
    this.flangerLfo = null;
    this.flangerLfoGain = null;
    this.masterOutputGain = null;
    this.recordingDestination = null;
    this.analyser = null;
  }

  subscribe(callback: () => void): () => void {
    this.onUpdateCallbacks.push(callback);
    return () => {
      this.onUpdateCallbacks = this.onUpdateCallbacks.filter((cb) => cb !== callback);
    };
  }

  private notify() {
    this.onUpdateCallbacks.forEach((cb) => cb());
  }
}

export const micEngine = new MicEngine();

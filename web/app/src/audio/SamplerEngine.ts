import { SamplePad } from '../types';
import { connectGlobalEqualizer, GlobalEqualizerConnection } from './GlobalEqualizer';

export class SamplerEngine {
  private ctx: AudioContext | null = null;
  private audioBuffers: Map<string, AudioBuffer> = new Map();
  private activeSources: Map<string, AudioBufferSourceNode> = new Map();
  private masterBus: GainNode | null = null;
  private globalEqConnection: GlobalEqualizerConnection | null = null;
  private fallbackAudioNodes = new Map<string, { audio: HTMLAudioElement; source: MediaElementAudioSourceNode; gain: GainNode }>();
  public volume = 0.9;
  public currentBank: 'A' | 'B' | 'C' | 'D' = 'A';

  public pads: Record<'A' | 'B' | 'C' | 'D', SamplePad[]> = {
    A: [
      { id: 'A_0', bank: 'A', index: 0, name: 'Laser Blast', category: 'DJ FX', assetPath: '/factory_fx/laserLarge_000.ogg', hotkey: '1' },
      { id: 'A_1', bank: 'A', index: 1, name: 'Laser Zap 1', category: 'DJ FX', assetPath: '/factory_fx/laserSmall_000.ogg', hotkey: '2' },
      { id: 'A_2', bank: 'A', index: 2, name: 'Laser Zap 2', category: 'DJ FX', assetPath: '/factory_fx/laserSmall_001.ogg', hotkey: '3' },
      { id: 'A_3', bank: 'A', index: 3, name: 'Digital Scan', category: 'DJ FX', assetPath: '/factory_fx/computerNoise_000.ogg', hotkey: '4' },
      { id: 'A_4', bank: 'A', index: 4, name: 'Force Pulse', category: 'DJ FX', assetPath: '/factory_fx/forceField_000.ogg', hotkey: 'Q' },
      { id: 'A_5', bank: 'A', index: 5, name: 'Force Charge', category: 'DJ FX', assetPath: '/factory_fx/forceField_001.ogg', hotkey: 'W' },
      { id: 'A_6', bank: 'A', index: 6, name: 'Engine Rev', category: 'DJ FX', assetPath: '/factory_fx/engineCircular_000.ogg', hotkey: 'E' },
      { id: 'A_7', bank: 'A', index: 7, name: 'Door Open', category: 'DJ FX', assetPath: '/factory_fx/doorOpen_000.ogg', hotkey: 'R' },
      { id: 'A_8', bank: 'A', index: 8, name: 'Door Close', category: 'DJ FX', assetPath: '/factory_fx/doorClose_000.ogg', hotkey: 'A' },
      { id: 'A_9', bank: 'A', index: 9, name: 'Impact Crunch', category: 'DJ FX', assetPath: '/factory_fx/explosionCrunch_000.ogg', hotkey: 'S' },
      { id: 'A_10', bank: 'A', index: 10, name: 'Crunch 2', category: 'DJ FX', assetPath: '/factory_fx/explosionCrunch_001.ogg', hotkey: 'D' },
      { id: 'A_11', bank: 'A', index: 11, name: 'Metal Hit', category: 'DJ FX', assetPath: '/factory_fx/impactMetal_000.ogg', hotkey: 'F' },
      { id: 'A_12', bank: 'A', index: 12, name: 'Sub Boom', category: 'DJ FX', assetPath: '/factory_fx/lowFrequency_explosion_000.ogg', hotkey: 'Z' },
      { id: 'A_13', bank: 'A', index: 13, name: 'Air Horn', category: 'DJ FX', assetPath: '/factory_fx/hype air horn.mp3', hotkey: 'X' },
      { id: 'A_14', bank: 'A', index: 14, name: 'Danger FX', category: 'DJ FX', assetPath: '/factory_fx/danger.mp3', hotkey: 'C' },
      { id: 'A_15', bank: 'A', index: 15, name: 'Money Cash', category: 'DJ FX', assetPath: '/factory_fx/money cash register purchase.mp3', hotkey: 'V' },
    ],
    B: [
      { id: 'B_0', bank: 'B', index: 0, name: 'Boing', category: 'Comedy', assetPath: '/factory_fx/boing cartoon.mp3', hotkey: '1' },
      { id: 'B_1', bank: 'B', index: 1, name: 'Bruh', category: 'Comedy', assetPath: '/factory_fx/bruh.mp3', hotkey: '2' },
      { id: 'B_2', bank: 'B', index: 2, name: 'Buzzer', category: 'Comedy', assetPath: '/factory_fx/buzzer.mp3', hotkey: '3' },
      { id: 'B_3', bank: 'B', index: 3, name: 'Confused Ehh', category: 'Comedy', assetPath: '/factory_fx/confused ehhh.mp3', hotkey: '4' },
      { id: 'B_4', bank: 'B', index: 4, name: 'Crickets', category: 'Comedy', assetPath: '/factory_fx/crickets bad joke.mp3', hotkey: 'Q' },
      { id: 'B_5', bank: 'B', index: 5, name: 'Evil Laugh', category: 'Comedy', assetPath: '/factory_fx/evil laughter.mp3', hotkey: 'W' },
      { id: 'B_6', bank: 'B', index: 6, name: 'Fart Long', category: 'Comedy', assetPath: '/factory_fx/fart long.mp3', hotkey: 'E' },
      { id: 'B_7', bank: 'B', index: 7, name: 'Fart Powerful', category: 'Comedy', assetPath: '/factory_fx/fart powerful.mp3', hotkey: 'R' },
      { id: 'B_8', bank: 'B', index: 8, name: 'Fart Short', category: 'Comedy', assetPath: '/factory_fx/fart short.mp3', hotkey: 'A' },
      { id: 'B_9', bank: 'B', index: 9, name: 'Fart Wet', category: 'Comedy', assetPath: '/factory_fx/fart wet.mp3', hotkey: 'S' },
      { id: 'B_10', bank: 'B', index: 10, name: 'Flute Slide', category: 'Comedy', assetPath: '/factory_fx/flute slide cartoon falling.mp3', hotkey: 'D' },
      { id: 'B_11', bank: 'B', index: 11, name: 'Golf Clap', category: 'Comedy', assetPath: '/factory_fx/golf clap.mp3', hotkey: 'F' },
      { id: 'B_12', bank: 'B', index: 12, name: 'Cute Laugh', category: 'Comedy', assetPath: '/factory_fx/laughter cute.mp3', hotkey: 'Z' },
      { id: 'B_13', bank: 'B', index: 13, name: 'Sitcom Crowd', category: 'Comedy', assetPath: '/factory_fx/laughter sitcom audience crowd.mp3', hotkey: 'X' },
      { id: 'B_14', bank: 'B', index: 14, name: 'Quack Duck', category: 'Comedy', assetPath: '/factory_fx/quack duck.mp3', hotkey: 'C' },
      { id: 'B_15', bank: 'B', index: 15, name: 'Nope!', category: 'Comedy', assetPath: '/factory_fx/nope.mp3', hotkey: 'V' },
    ],
    C: [
      { id: 'C_0', bank: 'C', index: 0, name: 'Air Horn', category: 'Trends', assetPath: '/factory_fx/hype air horn.mp3', hotkey: '1' },
      { id: 'C_1', bank: 'C', index: 1, name: 'Bye Bye', category: 'Trends', assetPath: '/factory_fx/bye bye.mp3', hotkey: '2' },
      { id: 'C_2', bank: 'C', index: 2, name: 'Bruh', category: 'Trends', assetPath: '/factory_fx/bruh.mp3', hotkey: '3' },
      { id: 'C_3', bank: 'C', index: 3, name: 'Correct!', category: 'Trends', assetPath: "/factory_fx/correct that's correct radio.mp3", hotkey: '4' },
      { id: 'C_4', bank: 'C', index: 4, name: 'Danger', category: 'Trends', assetPath: '/factory_fx/danger.mp3', hotkey: 'Q' },
      { id: 'C_5', bank: 'C', index: 5, name: 'Haters Gonna Hate', category: 'Trends', assetPath: '/factory_fx/haters gonna hate.mp3', hotkey: 'W' },
      { id: 'C_6', bank: 'C', index: 6, name: 'Money Cash', category: 'Trends', assetPath: '/factory_fx/money cash register purchase.mp3', hotkey: 'E' },
      { id: 'C_7', bank: 'C', index: 7, name: 'Nice Mmm', category: 'Trends', assetPath: '/factory_fx/nice mmm.mp3', hotkey: 'R' },
      { id: 'C_8', bank: 'C', index: 8, name: 'What?', category: 'Trends', assetPath: '/factory_fx/what short.mp3', hotkey: 'A' },
      { id: 'C_9', bank: 'C', index: 9, name: 'What?! (Surprised)', category: 'Trends', assetPath: '/factory_fx/what surprised.mp3', hotkey: 'S' },
      { id: 'C_10', bank: 'C', index: 10, name: 'Winning Jingle', category: 'Trends', assetPath: '/factory_fx/winning jingle.mp3', hotkey: 'D' },
      { id: 'C_11', bank: 'C', index: 11, name: 'Yeah Oh Yeah', category: 'Trends', assetPath: '/factory_fx/yeah ohh yeah.mp3', hotkey: 'F' },
      { id: 'C_12', bank: 'C', index: 12, name: 'Yeah Song', category: 'Trends', assetPath: '/factory_fx/yeah song.mp3', hotkey: 'Z' },
      { id: 'C_13', bank: 'C', index: 13, name: 'Yeet!', category: 'Trends', assetPath: '/factory_fx/yeet.mp3', hotkey: 'X' },
      { id: 'C_14', bank: 'C', index: 14, name: 'Wow!', category: 'Trends', assetPath: '/factory_fx/wow.mp3', hotkey: 'C' },
      { id: 'C_15', bank: 'C', index: 15, name: 'Sad Trombone', category: 'Trends', assetPath: '/factory_fx/fail game over wah wah sad trombone.mp3', hotkey: 'V' },
    ],
    D: [
      { id: 'D_0', bank: 'D', index: 0, name: 'Custom Pad 1', category: 'Custom', assetPath: '/factory_fx/laserLarge_000.ogg', hotkey: '1' },
      { id: 'D_1', bank: 'D', index: 1, name: 'Custom Pad 2', category: 'Custom', assetPath: '/factory_fx/computerNoise_000.ogg', hotkey: '2' },
      { id: 'D_2', bank: 'D', index: 2, name: 'Custom Pad 3', category: 'Custom', assetPath: '/factory_fx/hype air horn.mp3', hotkey: '3' },
      { id: 'D_3', bank: 'D', index: 3, name: 'Custom Pad 4', category: 'Custom', assetPath: '/factory_fx/bruh.mp3', hotkey: '4' },
      { id: 'D_4', bank: 'D', index: 4, name: 'Custom Pad 5', category: 'Custom', assetPath: '/factory_fx/wow.mp3', hotkey: 'Q' },
      { id: 'D_5', bank: 'D', index: 5, name: 'Custom Pad 6', category: 'Custom', assetPath: '/factory_fx/buzzer.mp3', hotkey: 'W' },
      { id: 'D_6', bank: 'D', index: 6, name: 'Custom Pad 7', category: 'Custom', assetPath: '/factory_fx/boing cartoon.mp3', hotkey: 'E' },
      { id: 'D_7', bank: 'D', index: 7, name: 'Custom Pad 8', category: 'Custom', assetPath: '/factory_fx/fart powerful.mp3', hotkey: 'R' },
      { id: 'D_8', bank: 'D', index: 8, name: 'Custom Pad 9', category: 'Custom', assetPath: '/factory_fx/doorOpen_000.ogg', hotkey: 'A' },
      { id: 'D_9', bank: 'D', index: 9, name: 'Custom Pad 10', category: 'Custom', assetPath: '/factory_fx/impactMetal_000.ogg', hotkey: 'S' },
      { id: 'D_10', bank: 'D', index: 10, name: 'Custom Pad 11', category: 'Custom', assetPath: '/factory_fx/forceField_000.ogg', hotkey: 'D' },
      { id: 'D_11', bank: 'D', index: 11, name: 'Custom Pad 12', category: 'Custom', assetPath: '/factory_fx/danger.mp3', hotkey: 'F' },
      { id: 'D_12', bank: 'D', index: 12, name: 'Custom Pad 13', category: 'Custom', assetPath: '/factory_fx/lowFrequency_explosion_000.ogg', hotkey: 'Z' },
      { id: 'D_13', bank: 'D', index: 13, name: 'Custom Pad 14', category: 'Custom', assetPath: '/factory_fx/yeet.mp3', hotkey: 'X' },
      { id: 'D_14', bank: 'D', index: 14, name: 'Custom Pad 15', category: 'Custom', assetPath: '/factory_fx/bye bye.mp3', hotkey: 'C' },
      { id: 'D_15', bank: 'D', index: 15, name: 'Custom Pad 16', category: 'Custom', assetPath: '/factory_fx/winning jingle.mp3', hotkey: 'V' },
    ],
  };

  private ensureContext(): AudioContext {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.ctx = new AudioCtx();
      this.masterBus = this.ctx.createGain();
      this.masterBus.gain.value = 1;
      this.globalEqConnection = connectGlobalEqualizer(this.ctx, this.masterBus, this.ctx.destination);
    }
    if (this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
    return this.ctx;
  }

  async triggerPad(pad: SamplePad): Promise<void> {
    const ctx = this.ensureContext();
    const fallback = this.fallbackAudioNodes.get(pad.id);
    if (fallback) {
      fallback.audio.pause();
      fallback.source.disconnect();
      fallback.gain.disconnect();
      this.fallbackAudioNodes.delete(pad.id);
    }
    try {
      let buffer = this.audioBuffers.get(pad.assetPath);
      if (!buffer) {
        const res = await fetch(pad.assetPath);
        const arrayBuffer = await res.arrayBuffer();
        buffer = await ctx.decodeAudioData(arrayBuffer);
        this.audioBuffers.set(pad.assetPath, buffer);
      }

      // Stop previous if playing same pad
      const prevSource = this.activeSources.get(pad.id);
      if (prevSource) {
        try {
          prevSource.stop();
        } catch {
          // ignore
        }
      }

      const source = ctx.createBufferSource();
      source.buffer = buffer;

      const gain = ctx.createGain();
      gain.gain.value = this.volume;

      source.connect(gain);
      if (this.masterBus) gain.connect(this.masterBus);
      else gain.connect(ctx.destination);

      source.onended = () => {
        this.activeSources.delete(pad.id);
      };

      source.start(0);
      this.activeSources.set(pad.id, source);
    } catch (err) {
      console.warn('Sampler playback fallback (routed through global EQ):', err);
      try {
        const fallbackContext = this.ensureContext();
        const audio = new Audio(pad.assetPath);
        audio.preload = 'auto';
        audio.volume = 1;
        const source = fallbackContext.createMediaElementSource(audio);
        const gain = fallbackContext.createGain();
        gain.gain.value = this.volume;
        source.connect(gain);
        if (this.masterBus) gain.connect(this.masterBus);
        else gain.connect(fallbackContext.destination);
        this.fallbackAudioNodes.set(pad.id, { audio, source, gain });
        audio.addEventListener('ended', () => {
          const active = this.fallbackAudioNodes.get(pad.id);
          if (active?.audio !== audio) return;
          this.fallbackAudioNodes.delete(pad.id);
          try { source.disconnect(); gain.disconnect(); } catch { /* already disconnected */ }
        }, { once: true });
        await audio.play();
      } catch (fallbackError) {
        console.warn('Sampler fallback could not play:', fallbackError);
      }
    }
  }

  setCustomPad(bank: 'A' | 'B' | 'C' | 'D', index: number, name: string, fileUrl: string) {
    if (this.pads[bank] && this.pads[bank][index]) {
      this.pads[bank][index].name = name;
      this.pads[bank][index].assetPath = fileUrl;
    }
  }

  stopAll() {
    for (const [, source] of this.activeSources) {
      try {
        source.stop();
      } catch {
        // ignore
      }
    }
    this.activeSources.clear();
    for (const [, entry] of this.fallbackAudioNodes) {
      try {
        entry.audio.pause();
        entry.audio.currentTime = 0;
        entry.source.disconnect();
        entry.gain.disconnect();
      } catch {
        // ignore
      }
    }
    this.fallbackAudioNodes.clear();
  }
}

export const samplerEngine = new SamplerEngine();

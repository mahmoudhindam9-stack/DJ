const VOICE_TUNINGS = {
  NONE: { pitch: 1, brightness: 0, cutoff: 12000, drive: 0, bass: 0, ring: 0 },
  WOMAN: { pitch: 1.2, brightness: 0.16, cutoff: 8800, drive: 0.03, bass: 0, ring: 0 },
  KID: { pitch: 1.43, brightness: 0.23, cutoff: 9400, drive: 0.025, bass: 0, ring: 0 },
  CHIPMUNK: { pitch: 1.72, brightness: 0.3, cutoff: 10200, drive: 0.015, bass: 0, ring: 0 },
  MONSTER: { pitch: 0.69, brightness: -0.14, cutoff: 5200, drive: 0.2, bass: 0.24, ring: 0 },
  DARK_DEMON: { pitch: 0.58, brightness: -0.2, cutoff: 4600, drive: 0.34, bass: 0.3, ring: 0.13 },
  GIANT_BASS: { pitch: 0.63, brightness: -0.24, cutoff: 4200, drive: 0.12, bass: 0.36, ring: 0 }
};
const FILTER_PITCH = {
  KID: 1.55, CHIPMUNK: 1.9, SMALL_WOMAN: 1.3, OLD_WOMAN: 1.15,
  OLD_MAN: 0.75, GIANT: 0.6, MONSTER: 0.52
};

class MicVoiceProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.voiceEffect = 'NONE';
    this.currentFilter = 'NONE';
    this.grainSize = 1024;
    this.ringSize = this.grainSize * 4;
    this.rings = [];
    this.writePositions = [];
    this.delay1 = [];
    this.delay2 = [];
    this.lowStates = [];
    this.airStates = [];
    this.robotPhases = [];
    this.chorusRings = [];
    this.chorusPositions = [];
    this.tremoloPhase = 0;
    this.port.onmessage = (event) => {
      const config = event.data || {};
      if (config.voiceEffect) this.voiceEffect = config.voiceEffect;
      if (config.currentFilter) this.currentFilter = config.currentFilter;
    };
  }

  ensureChannel(channel) {
    while (this.rings.length <= channel) {
      this.rings.push(new Float32Array(this.ringSize));
      this.writePositions.push(0);
      this.delay1.push(0);
      this.delay2.push(this.grainSize / 2);
      this.lowStates.push(0);
      this.airStates.push(0);
      this.robotPhases.push(0);
      this.chorusRings.push(new Float32Array(2400));
      this.chorusPositions.push(0);
    }
  }

  readInterpolated(channel, delay) {
    const ring = this.rings[channel];
    const write = this.writePositions[channel];
    const position = ((write - delay) % this.ringSize + this.ringSize) % this.ringSize;
    const i0 = Math.floor(position);
    const i1 = (i0 + 1) % this.ringSize;
    const fraction = position - i0;
    return ring[i0] * (1 - fraction) + ring[i1] * fraction;
  }

  pitchShift(input, channel, ratio) {
    this.rings[channel][this.writePositions[channel]] = input;
    this.writePositions[channel] = (this.writePositions[channel] + 1) % this.ringSize;
    const d1 = this.delay1[channel];
    const d2 = this.delay2[channel];
    const phase1 = Math.max(0, Math.min(1, d1 / this.grainSize));
    const phase2 = Math.max(0, Math.min(1, d2 / this.grainSize));
    const out1 = this.readInterpolated(channel, d1) * (0.5 - 0.5 * Math.cos(2 * Math.PI * phase1));
    const out2 = this.readInterpolated(channel, d2) * (0.5 - 0.5 * Math.cos(2 * Math.PI * phase2));
    const step = ratio - 1;
    let next1 = d1 - step;
    let next2 = d2 - step;
    if (next1 < 0) next1 += this.grainSize;
    else if (next1 >= this.grainSize) next1 -= this.grainSize;
    if (next2 < 0) next2 += this.grainSize;
    else if (next2 >= this.grainSize) next2 -= this.grainSize;
    this.delay1[channel] = next1;
    this.delay2[channel] = next2;
    return out1 + out2;
  }

  lowPass(input, channel, cutoff, air = false) {
    const states = air ? this.airStates : this.lowStates;
    const alpha = Math.exp(-2 * Math.PI * Math.max(40, Math.min(18000, cutoff)) / sampleRate);
    const output = alpha * states[channel] + (1 - alpha) * input;
    states[channel] = output;
    return output;
  }

  process(inputs, outputs) {
    const input = inputs[0];
    const output = outputs[0];
    if (!input || input.length === 0 || !output || output.length === 0) return true;

    const voice = VOICE_TUNINGS[this.voiceEffect] || VOICE_TUNINGS.NONE;
    const pitch = this.voiceEffect !== 'NONE' ? voice.pitch : (FILTER_PITCH[this.currentFilter] || 1);
    for (let channel = 0; channel < output.length; channel++) {
      this.ensureChannel(channel);
      const source = input[Math.min(channel, input.length - 1)];
      const target = output[channel];
      for (let i = 0; i < target.length; i++) {
        const dry = source[i] || 0;
        let sample = pitch === 1 ? dry : this.pitchShift(dry, channel, pitch);
        if (pitch === 1) {
          this.rings[channel][this.writePositions[channel]] = dry;
          this.writePositions[channel] = (this.writePositions[channel] + 1) % this.ringSize;
        }

        const low = this.lowPass(sample, channel, 190);
        const air = sample - this.lowPass(sample, channel, voice.cutoff, true);
        sample += air * voice.brightness + low * voice.bass;
        if (voice.drive > 0) {
          const drive = 1 + voice.drive * 7;
          sample = Math.tanh(sample * drive) / Math.tanh(drive);
        }

        const filter = this.currentFilter;
        if (filter === 'TELEPHONE') {
          const lowCut = this.lowPass(sample, channel, 480);
          const highTail = sample - this.lowPass(sample, channel, 2700, true);
          sample = (sample - lowCut + highTail) * 1.15;
        } else if (filter === 'MEGAPHONE') {
          sample = Math.tanh(this.lowPass(sample, channel, 3200) * 3.5);
        } else if (filter === 'RADIO') {
          sample = Math.tanh(this.lowPass(sample, channel, 3600) * 2.2);
        } else if (filter === 'ROBOT') {
          const phase = this.robotPhases[channel];
          const ring = Math.sin(phase * 2 * Math.PI);
          this.robotPhases[channel] = (phase + 140 / sampleRate) % 1;
          sample = sample * ring * 1.35;
        } else if (filter === 'CHORUS') {
          const ring = this.chorusRings[channel];
          const pos = this.chorusPositions[channel];
          ring[pos] = sample;
          const depth = 0.5 + 0.5 * Math.sin(2 * Math.PI * 0.42 * (currentFrame + i) / sampleRate);
          const delay = 420 + depth * 300;
          const read = (pos - Math.floor(delay) + ring.length) % ring.length;
          sample += ring[read] * 0.58;
          this.chorusPositions[channel] = (pos + 1) % ring.length;
        } else if (filter === 'TREMOLO') {
          sample *= 0.52 + 0.48 * Math.sin(2 * Math.PI * 5.2 * (currentFrame + i) / sampleRate);
        } else if (filter === 'BASS_BOOST' || filter === 'CLUB') {
          sample += low * (filter === 'CLUB' ? 1.1 : 0.82);
        } else if (filter === 'BRIGHT') {
          sample += air * 0.7;
        } else if (filter === 'WARM') {
          sample += low * 0.45;
        }

        target[i] = Math.max(-0.98, Math.min(0.98, sample));
      }
    }
    return true;
  }
}
registerProcessor('dj-mic-voice-processor', MicVoiceProcessor);
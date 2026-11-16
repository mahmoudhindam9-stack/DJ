import React, { useState, useEffect, useRef } from 'react';
import { AudioItem, SamplePad, ThemeColors } from '../../types';
import { deckAEngine, deckBEngine, DJEffectType } from '../../audio/DJDeckEngine';
import { samplerEngine } from '../../audio/SamplerEngine';
import { micEngine } from '../../audio/MicEngine';
import { mainAudioEngine } from '../../audio/AudioEngine';
import {
  Play,
  Pause,
  RotateCcw,
  Disc,
  Sliders,
  Zap,
  Sparkles,
  Mic,
  MicOff,
  CircleDot,
  Square,
  FolderOpen
} from 'lucide-react';
import { cloudSyncService } from '../../services/CloudSyncService';

interface Props {
  library?: AudioItem[];
  audioLibrary?: AudioItem[];
  themeColors?: ThemeColors;
  isArabic?: boolean;
  isAr?: boolean;
  onPauseMainPlayer: () => void;
  onImportFiles?: (files: FileList | File[]) => Promise<number>;
  externalLoadRequest?: { song: AudioItem; deck: 'A' | 'B'; id: string } | null;
  onClearExternalLoad?: () => void;
}

const toArabicDigits = (value: number | string): string =>
  String(value).replace(/\d/g, (d) => '٠١٢٣٤٥٦٧٨٩'[Number(d)]);

export const DJMixerScreen: React.FC<Props> = ({
  library,
  audioLibrary,
  isArabic,
  isAr,
  onPauseMainPlayer,
  onImportFiles,
  externalLoadRequest,
  onClearExternalLoad
}) => {
  const tracks = library || audioLibrary || [];
  const isRtlAr = Boolean(isArabic ?? isAr);

  // Deck A State
  const [songA, setSongA] = useState<AudioItem | null>(deckAEngine.currentTrack);
  const [playingA, setPlayingA] = useState(deckAEngine.isPlaying);
  const [posAMs, setPosAMs] = useState(deckAEngine.currentTimeMs);
  const [durAMs, setDurAMs] = useState(deckAEngine.durationMs);
  const [volA, setVolA] = useState(deckAEngine.volume);
  const [speedA, setSpeedA] = useState(deckAEngine.pitch);
  const [bassA, setBassA] = useState(0);
  const [midA, setMidA] = useState(0);
  const [highA, setHighA] = useState(0);
  const [loopA, setLoopA] = useState(deckAEngine.loopBeats > 0);

  // Deck B State
  const [songB, setSongB] = useState<AudioItem | null>(deckBEngine.currentTrack);
  const [playingB, setPlayingB] = useState(deckBEngine.isPlaying);
  const [posBMs, setPosBMs] = useState(deckBEngine.currentTimeMs);
  const [durBMs, setDurBMs] = useState(deckBEngine.durationMs);
  const [volB, setVolB] = useState(deckBEngine.volume);
  const [speedB, setSpeedB] = useState(deckBEngine.pitch);
  const [bassB, setBassB] = useState(0);
  const [midB, setMidB] = useState(0);
  const [highB, setHighB] = useState(0);
  const [loopB, setLoopB] = useState(deckBEngine.loopBeats > 0);

  // Crossfader (-1 = Full A, 0 = Center, 1 = Full B)
  const [crossfader, setCrossfader] = useState(0);

  // Center FX & 10-band EQ & Sampler Bank
  const [activePadBank, setActivePadBank] = useState<'A' | 'B' | 'C' | 'D'>('A');
  const [activeFx, setActiveFx] = useState<DJEffectType>('fx_delay');
  const [fxWet, setFxWet] = useState<number>(0.35);
  const [masterEqBands, setMasterEqBands] = useState<number[]>([2, 1, 0, 0, 1, 1, 0, 1, 2, 2]);
  const [activePadId, setActivePadId] = useState<string | null>(null);

  // Mic & Recording
  const [micActive, setMicActive] = useState(micEngine.isMicEnabled);
  const [isRecording, setIsRecording] = useState(micEngine.isRecording);
  const [pickerDeck, setPickerDeck] = useState<'A' | 'B' | null>(null);

  const fileInputARef = useRef<HTMLInputElement | null>(null);
  const fileInputBRef = useRef<HTMLInputElement | null>(null);

  // Listen for remote audio output claims (Single Active Audio Output)
  useEffect(() => {
    const handleRemoteClaim = () => {
      deckAEngine.pause();
      deckBEngine.pause();
      setPlayingA(false);
      setPlayingB(false);
    };
    window.addEventListener('dj-remote-audio-claimed', handleRemoteClaim);
    return () => {
      window.removeEventListener('dj-remote-audio-claimed', handleRemoteClaim);
    };
  }, []);

  // Subscribe to Deck A & Deck B updates
  useEffect(() => {
    const syncDecks = () => {
      setSongA(deckAEngine.currentTrack);
      setPlayingA(deckAEngine.isPlaying);
      setPosAMs(deckAEngine.currentTimeMs);
      setDurAMs(deckAEngine.durationMs);
      setSpeedA(deckAEngine.pitch);
      setLoopA(deckAEngine.loopBeats > 0);

      setSongB(deckBEngine.currentTrack);
      setPlayingB(deckBEngine.isPlaying);
      setPosBMs(deckBEngine.currentTimeMs);
      setDurBMs(deckBEngine.durationMs);
      setSpeedB(deckBEngine.pitch);
      setLoopB(deckBEngine.loopBeats > 0);
    };
    const unsubA = deckAEngine.subscribe(syncDecks);
    const unsubB = deckBEngine.subscribe(syncDecks);
    const timer = window.setInterval(syncDecks, 180);
    return () => {
      unsubA();
      unsubB();
      window.clearInterval(timer);
    };
  }, []);

  // Apply crossfader law to Deck A & Deck B
  useEffect(() => {
    const gainA = crossfader <= 0 ? 1 : Math.max(0, 1 - crossfader);
    const gainB = crossfader >= 0 ? 1 : Math.max(0, 1 + crossfader);
    deckAEngine.setDeckVolume(volA);
    deckBEngine.setDeckVolume(volB);
    deckAEngine.setCrossfadeVolume(gainA);
    deckBEngine.setCrossfadeVolume(gainB);
  }, [crossfader, volA, volB]);

  // Handle external load requests from Library / Online Music
  useEffect(() => {
    if (!externalLoadRequest) return;
    loadToDeck(externalLoadRequest.deck, externalLoadRequest.song);
    onClearExternalLoad?.();
  }, [externalLoadRequest]);

  const loadToDeck = (deck: 'A' | 'B', item: AudioItem) => {
    onPauseMainPlayer();
    if (deck === 'A') {
      deckAEngine.loadTrack(item);
      setSongA(item);
      setPosAMs(0);
    } else {
      deckBEngine.loadTrack(item);
      setSongB(item);
      setPosBMs(0);
    }
    setPickerDeck(null);
  };

  const handleLocalFilePick = async (deck: 'A' | 'B', e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    const file = files[0];
    if (onImportFiles) {
      await onImportFiles(files);
    }
    const uri = URL.createObjectURL(file);
    const item: AudioItem = {
      id: `dj_${deck}_${Date.now()}`,
      title: file.name.replace(/\.[^/.]+$/, ''),
      artist: `Deck ${deck} Local`,
      album: 'DJ Studio',
      duration: 0,
      uri,
      addedDate: Date.now()
    };
    loadToDeck(deck, item);
  };

  const togglePlayDeck = (deck: 'A' | 'B') => {
    onPauseMainPlayer();
    if (deck === 'A') {
      if (!deckAEngine.currentTrack && tracks.length > 0) {
        loadToDeck('A', tracks[0]);
      }
      if (deckAEngine.isPlaying) {
        deckAEngine.pause();
        setPlayingA(false);
      } else {
        void deckAEngine.play();
        setPlayingA(true);
        void cloudSyncService.claimWebAudioOutput({
          isPlaying: true,
          currentTrack: deckAEngine.currentTrack || tracks[0] || null,
          positionMs: deckAEngine.currentTimeMs
        });
      }
    } else {
      if (!deckBEngine.currentTrack && tracks.length > 0) {
        loadToDeck('B', tracks[Math.min(1, tracks.length - 1)]);
      }
      if (deckBEngine.isPlaying) {
        deckBEngine.pause();
        setPlayingB(false);
      } else {
        void deckBEngine.play();
        setPlayingB(true);
        void cloudSyncService.claimWebAudioOutput({
          isPlaying: true,
          currentTrack: deckBEngine.currentTrack || tracks[0] || null,
          positionMs: deckBEngine.currentTimeMs
        });
      }
    }
  };

  const triggerPad = (pad: SamplePad) => {
    setActivePadId(pad.id);
    void samplerEngine.triggerPad(pad);
    window.setTimeout(() => {
      setActivePadId((prev) => (prev === pad.id ? null : prev));
    }, 180);
  };

  const toggleMic = async () => {
    const enabled = await micEngine.toggleMic();
    setMicActive(enabled);
  };

  const toggleMasterRecord = async () => {
    if (!micEngine.isMicEnabled) {
      const ok = await micEngine.toggleMic(true);
      setMicActive(ok);
    }
    if (isRecording) {
      micEngine.stopRecordingAndDownload('DJ_Studio_Mix');
      setIsRecording(false);
    } else {
      const started = micEngine.startRecording();
      setIsRecording(started);
    }
  };

  const formatMs = (ms: number) => {
    const sec = Math.max(0, Math.floor((ms || 0) / 1000));
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  const visiblePads = (samplerEngine.pads[activePadBank] || []).slice(0, 16);
  const eqFreqLabels = ['31', '62', '125', '250', '500', '1k', '2k', '4k', '8k', '16k'];

  return (
    <div
      data-testid="dj-mixer-full-viewport"
      className="flex-1 h-full w-full overflow-hidden bg-slate-950 text-white grid grid-cols-12 gap-2 p-2 select-none"
    >
      {/* Hidden local file inputs */}
      <input
        ref={fileInputARef}
        type="file"
        accept="audio/*"
        className="hidden"
        onChange={(e) => void handleLocalFilePick('A', e)}
      />
      <input
        ref={fileInputBRef}
        type="file"
        accept="audio/*"
        className="hidden"
        onChange={(e) => void handleLocalFilePick('B', e)}
      />

      {/* ==================== DECK A (LEFT 4 COLS) ==================== */}
      <div
        data-testid="dj-deck-a-panel"
        className="col-span-4 h-full min-h-0 bg-slate-900/95 border border-cyan-500/30 rounded-2xl p-2.5 flex flex-col justify-between overflow-hidden shadow-lg"
      >
        {/* Deck A Header & Track Loader */}
        <div className="flex items-center justify-between gap-2 bg-slate-950/80 border border-slate-800 rounded-xl px-2.5 py-1.5 shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            <span className="px-2 py-0.5 rounded-md bg-cyan-500/20 border border-cyan-400/40 text-cyan-300 font-black text-xs">
              DECK A
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-white truncate">
                {songA ? songA.title : isRtlAr ? 'اختر مقطعاً لـ Deck A' : 'Load Track to Deck A'}
              </div>
              <div className="text-[10px] text-cyan-300/80 font-mono">
                {formatMs(posAMs)} / {formatMs(durAMs)} • {((deckAEngine.currentBpm || 128) * speedA).toFixed(1)} BPM
              </div>
            </div>
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <button
              onClick={() => setPickerDeck(pickerDeck === 'A' ? null : 'A')}
              className="px-2 py-1 rounded-lg bg-cyan-600/20 hover:bg-cyan-600/35 border border-cyan-500/40 text-cyan-200 text-[10px] font-semibold"
            >
              {isRtlAr ? 'المكتبة' : 'Library'}
            </button>
            <button
              onClick={() => fileInputARef.current?.click()}
              className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-300"
              title={isRtlAr ? 'ملف صوتي' : 'Open File'}
            >
              <FolderOpen className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Deck A Jog Wheel + Pitch Slider */}
        <div className="flex items-center justify-around gap-2 my-1 flex-1 min-h-0">
          <div
            onClick={() => togglePlayDeck('A')}
            className={`relative w-32 h-32 xl:w-36 xl:h-36 rounded-full border-4 cursor-pointer flex items-center justify-center transition-transform ${
              playingA
                ? 'border-cyan-400 shadow-[0_0_28px_rgba(34,211,238,0.45)] animate-spin'
                : 'border-slate-700 hover:border-cyan-500/60'
            }`}
            style={{ animationDuration: `${Math.max(1.2, 3.2 / speedA)}s` }}
          >
            <div className="w-24 h-24 rounded-full bg-gradient-to-br from-slate-900 via-slate-950 to-cyan-950 border border-slate-700 flex items-center justify-center">
              <div className="w-9 h-9 rounded-full bg-cyan-500/20 border border-cyan-400 flex items-center justify-center">
                <Disc className="w-5 h-5 text-cyan-300" />
              </div>
            </div>
          </div>

          {/* Pitch / Tempo Fader */}
          <div className="flex flex-col items-center gap-1 bg-slate-950/70 border border-slate-800 rounded-xl px-2 py-2">
            <span className="text-[9px] text-slate-400 font-bold">TEMPO</span>
            <input
              type="range"
              min={0.5}
              max={1.5}
              step={0.01}
              value={speedA}
              onChange={(e) => {
                const v = Number(e.target.value);
                setSpeedA(v);
                deckAEngine.setPitch(v);
              }}
              className="w-20 accent-cyan-400 cursor-pointer"
            />
            <span className="text-[10px] font-mono text-cyan-300">
              {(speedA * 100).toFixed(0)}%
            </span>
          </div>
        </div>

        {/* Deck A Seekbar */}
        <div className="flex items-center gap-2 bg-slate-950/80 rounded-lg px-2 py-1 shrink-0">
          <input
            type="range"
            min={0}
            max={durAMs > 0 ? durAMs : 100000}
            step={250}
            value={posAMs}
            onChange={(e) => {
              const t = Number(e.target.value);
              setPosAMs(t);
              deckAEngine.seekTo(t);
            }}
            className="w-full accent-cyan-400 h-1.5 cursor-pointer"
          />
        </div>

        {/* Deck A Transport & Hot Cues */}
        <div className="grid grid-cols-5 gap-1.5 shrink-0">
          <button
            onClick={() => togglePlayDeck('A')}
            className={`py-1.5 rounded-xl font-bold text-xs flex items-center justify-center gap-1 ${
              playingA
                ? 'bg-cyan-500 text-slate-950 shadow-md'
                : 'bg-cyan-600/25 border border-cyan-500/40 text-cyan-200 hover:bg-cyan-600/40'
            }`}
          >
            {playingA ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
            <span>{playingA ? 'PAUSE' : 'PLAY'}</span>
          </button>

          <button
            onClick={() => {
              if (deckAEngine.cuePositionMs === 0) {
                deckAEngine.setCue();
              } else {
                deckAEngine.cue();
              }
            }}
            className="py-1.5 rounded-xl bg-amber-500/20 border border-amber-500/40 text-amber-300 text-[11px] font-bold"
          >
            CUE
          </button>

          <button
            onClick={() => {
              if (!deckAEngine.syncTo(deckBEngine).success) {
                setSpeedA(speedB);
                deckAEngine.setPitch(speedB);
              }
            }}
            className="py-1.5 rounded-xl bg-blue-500/20 border border-blue-500/40 text-blue-300 text-[11px] font-bold"
          >
            SYNC
          </button>

          <button
            onClick={() => {
              const nextBeats = loopA ? 0 : 4;
              deckAEngine.setLoopBeats(nextBeats);
              setLoopA(nextBeats > 0);
            }}
            className={`py-1.5 rounded-xl text-[11px] font-bold border ${
              loopA
                ? 'bg-emerald-500 text-slate-950 border-emerald-400'
                : 'bg-slate-800 text-slate-300 border-slate-700'
            }`}
          >
            LOOP
          </button>

          <button
            onClick={() => {
              deckAEngine.seekTo(0);
              setPosAMs(0);
            }}
            className="py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 flex items-center justify-center"
            title="Reset"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* Deck A 3-Band EQ + Channel Volume */}
        <div className="grid grid-cols-4 gap-1.5 bg-slate-950/80 border border-slate-800/80 rounded-xl p-2 shrink-0">
          {[
            { label: 'BASS', val: bassA, set: (v: number) => setBassA(v) },
            { label: 'MID', val: midA, set: (v: number) => setMidA(v) },
            { label: 'HIGH', val: highA, set: (v: number) => setHighA(v) },
            { label: 'GAIN', val: Math.round(volA * 100), set: (v: number) => setVolA(v / 100), min: 0, max: 100 }
          ].map((ctrl) => (
            <div key={ctrl.label} className="flex flex-col items-center">
              <span className="text-[9px] font-bold text-slate-400">{ctrl.label}</span>
              <input
                type="range"
                min={ctrl.min ?? -12}
                max={ctrl.max ?? 12}
                step={1}
                value={ctrl.val}
                onChange={(e) => ctrl.set(Number(e.target.value))}
                className="w-full accent-cyan-400 h-1 cursor-pointer my-1"
              />
              <span className="text-[9px] font-mono text-cyan-300">{ctrl.val}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ==================== CENTER MIXER, EQ, FX & SAMPLER PADS (CENTER 4 COLS) ==================== */}
      <div
        data-testid="dj-center-mixer-panel"
        className="col-span-4 h-full min-h-0 bg-slate-900/95 border border-purple-500/30 rounded-2xl p-2.5 flex flex-col justify-between overflow-hidden shadow-lg"
      >
        {/* Top Row: Master Console Header + Mic + Record */}
        <div className="flex items-center justify-between gap-1.5 bg-slate-950/90 border border-slate-800 rounded-xl px-2.5 py-1.5 shrink-0">
          <div className="flex items-center gap-1.5">
            <Sliders className="w-4 h-4 text-purple-400" />
            <span className="text-xs font-extrabold tracking-wider text-white">
              {isRtlAr ? 'وحدة المزج الاحترافية' : 'PRO MIXER CONSOLE'}
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => void toggleMic()}
              className={`px-2 py-1 rounded-lg text-[10px] font-bold flex items-center gap-1 border ${
                micActive
                  ? 'bg-emerald-500/25 border-emerald-400 text-emerald-200'
                  : 'bg-slate-800 border-slate-700 text-slate-300'
              }`}
            >
              {micActive ? <Mic className="w-3 h-3" /> : <MicOff className="w-3 h-3" />}
              <span>MIC</span>
            </button>
            <button
              onClick={() => void toggleMasterRecord()}
              className={`px-2 py-1 rounded-lg text-[10px] font-bold flex items-center gap-1 border ${
                isRecording
                  ? 'bg-red-600 text-white border-red-400 animate-pulse'
                  : 'bg-slate-800 border-slate-700 text-slate-300'
              }`}
            >
              {isRecording ? <Square className="w-3 h-3" /> : <CircleDot className="w-3 h-3 text-red-400" />}
              <span>{isRecording ? 'REC' : 'MIX REC'}</span>
            </button>
          </div>
        </div>

        {/* Crossfader A <-> B */}
        <div className="bg-slate-950/85 border border-slate-800 rounded-xl px-3 py-1.5 shrink-0">
          <div className="flex items-center justify-between text-[10px] font-bold mb-1">
            <span className="text-cyan-400">◄ DECK A</span>
            <button
              onClick={() => setCrossfader(0)}
              className="text-slate-400 hover:text-white"
            >
              CROSSFADER ({crossfader === 0 ? 'CENTER' : crossfader < 0 ? `A ${Math.round(-crossfader * 100)}%` : `B ${Math.round(crossfader * 100)}%`})
            </button>
            <span className="text-pink-400">DECK B ►</span>
          </div>
          <input
            type="range"
            min={-1}
            max={1}
            step={0.02}
            value={crossfader}
            onChange={(e) => setCrossfader(Number(e.target.value))}
            className="w-full accent-purple-500 h-2 cursor-pointer"
          />
        </div>

        {/* 10-Band Compact Master Equalizer + Beat FX */}
        <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-2 shrink-0">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[10px] font-bold text-purple-300 flex items-center gap-1">
              <Sparkles className="w-3 h-3" />
              {isRtlAr ? 'المعادل والمؤثرات الصوتية' : '10-BAND EQ & BEAT FX'}
            </span>
            <div className="flex items-center gap-1">
              {(
                [
                  { id: 'fx_delay', label: 'ECHO' },
                  { id: 'fx_reverb', label: 'REVERB' },
                  { id: 'fx_flanger', label: 'FLANGER' },
                  { id: 'fx_filter', label: 'FILTER' }
                ] as const
              ).map((fx) => (
                <button
                  key={fx.id}
                  onClick={() => {
                    setActiveFx(fx.id);
                    deckAEngine.setEffect(fx.id);
                    deckBEngine.setEffect(fx.id);
                  }}
                  className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                    activeFx === fx.id
                      ? 'bg-purple-600 text-white'
                      : 'bg-slate-800 text-slate-400 hover:text-white'
                  }`}
                >
                  {fx.label}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-10 gap-1 items-center pt-1">
            {masterEqBands.map((dbVal, idx) => (
              <div key={eqFreqLabels[idx]} className="flex flex-col items-center">
                <input
                  type="range"
                  min={-12}
                  max={12}
                  step={1}
                  value={dbVal}
                  onChange={(e) => {
                    const next = [...masterEqBands];
                    next[idx] = Number(e.target.value);
                    setMasterEqBands(next);
                    mainAudioEngine.applyCustomBands(next);
                  }}
                  className="w-full accent-purple-400 h-1 cursor-pointer"
                />
                <span className="text-[8px] text-slate-400 font-mono mt-0.5">
                  {eqFreqLabels[idx]}
                </span>
              </div>
            ))}
          </div>

          <div className="flex items-center gap-2 mt-1.5 pt-1 border-t border-slate-800/80">
            <span className="text-[9px] font-bold text-slate-400">FX DEPTH</span>
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={fxWet}
              onChange={(e) => {
                const amt = Number(e.target.value);
                setFxWet(amt);
                deckAEngine.setFxAmount(amt);
                deckBEngine.setFxAmount(amt);
              }}
              className="flex-1 accent-purple-400 h-1 cursor-pointer"
            />
            <span className="text-[9px] font-mono text-purple-300">
              {Math.round(fxWet * 100)}%
            </span>
          </div>
        </div>

        {/* 16/64 Sampler Pads Matrix (Fits cleanly inside center column with zero page scroll) */}
        <div className="flex-1 min-h-0 bg-slate-950/85 border border-slate-800 rounded-xl p-2 flex flex-col justify-between overflow-hidden">
          <div className="flex items-center justify-between mb-1 shrink-0">
            <span className="text-[10px] font-bold text-amber-300 flex items-center gap-1">
              <Zap className="w-3 h-3" />
              {isRtlAr ? 'لوحة المؤثرات اللحظية (٦٤ مؤثر)' : '64 SAMPLER PADS'}
            </span>
            <div className="flex items-center gap-1">
              {(['A', 'B', 'C', 'D'] as const).map((bank) => (
                <button
                  key={bank}
                  onClick={() => setActivePadBank(bank)}
                  className={`px-2 py-0.5 rounded text-[10px] font-extrabold ${
                    activePadBank === bank
                      ? 'bg-amber-500 text-slate-950'
                      : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                  }`}
                >
                  {isRtlAr ? `بنك ${bank}` : `BANK ${bank}`}
                </button>
              ))}
            </div>
          </div>

          <div className="flex-1 min-h-0 grid grid-cols-4 grid-rows-4 gap-1.5">
            {visiblePads.map((pad, idx) => {
              const isTriggered = activePadId === pad.id;
              return (
                <button
                  key={pad.id}
                  onClick={() => triggerPad(pad)}
                  className={`rounded-lg border text-[10px] font-bold px-1 flex flex-col items-center justify-center transition ${
                    isTriggered
                      ? 'bg-amber-400 text-slate-950 border-white scale-95 shadow-lg'
                      : 'bg-slate-900/90 hover:bg-slate-800 text-slate-200 border-amber-500/30'
                  }`}
                >
                  <span className="text-[9px] text-amber-400 font-mono">
                    #{isRtlAr ? toArabicDigits(idx + 1) : idx + 1}
                  </span>
                  <span className="truncate w-full text-center leading-tight">{pad.name}</span>
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* ==================== DECK B (RIGHT 4 COLS) ==================== */}
      <div
        data-testid="dj-deck-b-panel"
        className="col-span-4 h-full min-h-0 bg-slate-900/95 border border-pink-500/30 rounded-2xl p-2.5 flex flex-col justify-between overflow-hidden shadow-lg"
      >
        {/* Deck B Header & Track Loader */}
        <div className="flex items-center justify-between gap-2 bg-slate-950/80 border border-slate-800 rounded-xl px-2.5 py-1.5 shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            <span className="px-2 py-0.5 rounded-md bg-pink-500/20 border border-pink-400/40 text-pink-300 font-black text-xs">
              DECK B
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-white truncate">
                {songB ? songB.title : isRtlAr ? 'اختر مقطعاً لـ Deck B' : 'Load Track to Deck B'}
              </div>
              <div className="text-[10px] text-pink-300/80 font-mono">
                {formatMs(posBMs)} / {formatMs(durBMs)} • {((deckBEngine.currentBpm || 128) * speedB).toFixed(1)} BPM
              </div>
            </div>
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <button
              onClick={() => setPickerDeck(pickerDeck === 'B' ? null : 'B')}
              className="px-2 py-1 rounded-lg bg-pink-600/20 hover:bg-pink-600/35 border border-pink-500/40 text-pink-200 text-[10px] font-semibold"
            >
              {isRtlAr ? 'المكتبة' : 'Library'}
            </button>
            <button
              onClick={() => fileInputBRef.current?.click()}
              className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-pink-300"
              title={isRtlAr ? 'ملف صوتي' : 'Open File'}
            >
              <FolderOpen className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Deck B Jog Wheel + Pitch Slider */}
        <div className="flex items-center justify-around gap-2 my-1 flex-1 min-h-0">
          <div
            onClick={() => togglePlayDeck('B')}
            className={`relative w-32 h-32 xl:w-36 xl:h-36 rounded-full border-4 cursor-pointer flex items-center justify-center transition-transform ${
              playingB
                ? 'border-pink-400 shadow-[0_0_28px_rgba(244,114,182,0.45)] animate-spin'
                : 'border-slate-700 hover:border-pink-500/60'
            }`}
            style={{ animationDuration: `${Math.max(1.2, 3.2 / speedB)}s` }}
          >
            <div className="w-24 h-24 rounded-full bg-gradient-to-br from-slate-900 via-slate-950 to-pink-950 border border-slate-700 flex items-center justify-center">
              <div className="w-9 h-9 rounded-full bg-pink-500/20 border border-pink-400 flex items-center justify-center">
                <Disc className="w-5 h-5 text-pink-300" />
              </div>
            </div>
          </div>

          {/* Pitch / Tempo Fader */}
          <div className="flex flex-col items-center gap-1 bg-slate-950/70 border border-slate-800 rounded-xl px-2 py-2">
            <span className="text-[9px] text-slate-400 font-bold">TEMPO</span>
            <input
              type="range"
              min={0.5}
              max={1.5}
              step={0.01}
              value={speedB}
              onChange={(e) => {
                const v = Number(e.target.value);
                setSpeedB(v);
                deckBEngine.setPitch(v);
              }}
              className="w-20 accent-pink-400 cursor-pointer"
            />
            <span className="text-[10px] font-mono text-pink-300">
              {(speedB * 100).toFixed(0)}%
            </span>
          </div>
        </div>

        {/* Deck B Seekbar */}
        <div className="flex items-center gap-2 bg-slate-950/80 rounded-lg px-2 py-1 shrink-0">
          <input
            type="range"
            min={0}
            max={durBMs > 0 ? durBMs : 100000}
            step={250}
            value={posBMs}
            onChange={(e) => {
              const t = Number(e.target.value);
              setPosBMs(t);
              deckBEngine.seekTo(t);
            }}
            className="w-full accent-pink-400 h-1.5 cursor-pointer"
          />
        </div>

        {/* Deck B Transport & Hot Cues */}
        <div className="grid grid-cols-5 gap-1.5 shrink-0">
          <button
            onClick={() => togglePlayDeck('B')}
            className={`py-1.5 rounded-xl font-bold text-xs flex items-center justify-center gap-1 ${
              playingB
                ? 'bg-pink-500 text-slate-950 shadow-md'
                : 'bg-pink-600/25 border border-pink-500/40 text-pink-200 hover:bg-pink-600/40'
            }`}
          >
            {playingB ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
            <span>{playingB ? 'PAUSE' : 'PLAY'}</span>
          </button>

          <button
            onClick={() => {
              if (deckBEngine.cuePositionMs === 0) {
                deckBEngine.setCue();
              } else {
                deckBEngine.cue();
              }
            }}
            className="py-1.5 rounded-xl bg-amber-500/20 border border-amber-500/40 text-amber-300 text-[11px] font-bold"
          >
            CUE
          </button>

          <button
            onClick={() => {
              if (!deckBEngine.syncTo(deckAEngine).success) {
                setSpeedB(speedA);
                deckBEngine.setPitch(speedA);
              }
            }}
            className="py-1.5 rounded-xl bg-blue-500/20 border border-blue-500/40 text-blue-300 text-[11px] font-bold"
          >
            SYNC
          </button>

          <button
            onClick={() => {
              const nextBeats = loopB ? 0 : 4;
              deckBEngine.setLoopBeats(nextBeats);
              setLoopB(nextBeats > 0);
            }}
            className={`py-1.5 rounded-xl text-[11px] font-bold border ${
              loopB
                ? 'bg-emerald-500 text-slate-950 border-emerald-400'
                : 'bg-slate-800 text-slate-300 border-slate-700'
            }`}
          >
            LOOP
          </button>

          <button
            onClick={() => {
              deckBEngine.seekTo(0);
              setPosBMs(0);
            }}
            className="py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 flex items-center justify-center"
            title="Reset"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* Deck B 3-Band EQ + Channel Volume */}
        <div className="grid grid-cols-4 gap-1.5 bg-slate-950/80 border border-slate-800/80 rounded-xl p-2 shrink-0">
          {[
            { label: 'BASS', val: bassB, set: (v: number) => setBassB(v) },
            { label: 'MID', val: midB, set: (v: number) => setMidB(v) },
            { label: 'HIGH', val: highB, set: (v: number) => setHighB(v) },
            { label: 'GAIN', val: Math.round(volB * 100), set: (v: number) => setVolB(v / 100), min: 0, max: 100 }
          ].map((ctrl) => (
            <div key={ctrl.label} className="flex flex-col items-center">
              <span className="text-[9px] font-bold text-slate-400">{ctrl.label}</span>
              <input
                type="range"
                min={ctrl.min ?? -12}
                max={ctrl.max ?? 12}
                step={1}
                value={ctrl.val}
                onChange={(e) => ctrl.set(Number(e.target.value))}
                className="w-full accent-pink-400 h-1 cursor-pointer my-1"
              />
              <span className="text-[9px] font-mono text-pink-300">{ctrl.val}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Modal Track Picker Overlay (Only shown when user clicks Library on Deck A or Deck B) */}
      {pickerDeck && (
        <div className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-6">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl w-full max-w-lg max-h-[75vh] flex flex-col overflow-hidden">
            <div className="flex items-center justify-between px-4 py-3 border-b border-slate-800">
              <h3 className="text-sm font-bold text-white">
                {isRtlAr
                  ? `اختر مقطعاً لتحميله في Deck ${pickerDeck}`
                  : `Select Track for Deck ${pickerDeck}`}
              </h3>
              <button
                onClick={() => setPickerDeck(null)}
                className="px-2.5 py-1 rounded-lg bg-slate-800 text-xs text-slate-300 hover:text-white"
              >
                {isRtlAr ? 'إغلاق' : 'Close'}
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-3 space-y-1.5">
              {tracks.length === 0 ? (
                <p className="text-xs text-slate-400 text-center py-8">
                  {isRtlAr
                    ? 'المكتبة فارغة حالياً. استخدم زر الملف الصوتي لاختيار أغنية من جهازك.'
                    : 'Your library is empty. Use the folder icon to pick an audio file from your device.'}
                </p>
              ) : (
                tracks.map((track) => (
                  <button
                    key={track.id}
                    onClick={() => loadToDeck(pickerDeck, track)}
                    className="w-full text-left px-3 py-2 rounded-xl bg-slate-950/80 hover:bg-slate-800 flex items-center justify-between"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-bold text-white truncate">{track.title}</div>
                      <div className="text-[10px] text-slate-400 truncate">{track.artist}</div>
                    </div>
                    <span className="px-2 py-0.5 rounded bg-blue-600/20 text-blue-300 text-[10px] font-bold">
                      LOAD {pickerDeck}
                    </span>
                  </button>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

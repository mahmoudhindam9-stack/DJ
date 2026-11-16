import React, { useState, useEffect, useRef } from 'react';
import {
  Disc,
  Play,
  Pause,
  RotateCcw,
  Volume2,
  Sliders,
  Sparkles,
  Layers,
  Check,
  FolderOpen,
  X,
  FolderPlus,
  FileAudio,
} from 'lucide-react';
import { AudioItem, ThemeColors, SamplePad } from '../../types';
import { deckAEngine, deckBEngine, DJEffectType } from '../../audio/DJDeckEngine';
import { samplerEngine } from '../../audio/SamplerEngine';
import { AUDIO_INPUT_ACCEPT, pickLocalAudioFolder } from '../../utils/fileImporter';
import './DJMixerScreen.css';

type MixerBank = 'A' | 'B' | 'C' | 'D';

interface SavedDeckSettings {
  volume: number;
  pitch: number;
  effect: DJEffectType;
  fxAmount: number;
}

interface SavedMixerSettings {
  crossfader: number;
  selectedBank: MixerBank;
  samplerVolume: number;
  deckA: SavedDeckSettings;
  deckB: SavedDeckSettings;
}

const DJ_MIXER_STATE_KEY = 'dj-mixer-state-v1';
const SAVED_DJ_EFFECTS: DJEffectType[] = [
  'none',
  'fx_filter',
  'fx_delay',
  'fx_reverb',
  'fx_flanger',
  'fx_phaser',
  'fx_bitcrush',
  'fx_distortion',
  'fx_compressor',
  'voice_chipmunk',
  'voice_robot',
  'voice_monster',
  'voice_demon',
  'voice_giant',
  'voice_kid',
  'voice_woman',
  'voice_telephone',
  'voice_radio',
  'voice_alien',
];

const clampMixerValue = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(max, Number.isFinite(value) ? value : min));

function readSavedMixerSettings(): SavedMixerSettings {
  const defaults: SavedMixerSettings = {
    crossfader: 0.5,
    selectedBank: 'A',
    samplerVolume: 0.9,
    deckA: { volume: 1, pitch: 1, effect: 'none', fxAmount: 0.5 },
    deckB: { volume: 1, pitch: 1, effect: 'none', fxAmount: 0.5 },
  };
  if (typeof window === 'undefined') return defaults;

  try {
    const serialized = window.localStorage.getItem(DJ_MIXER_STATE_KEY);
    if (!serialized) return defaults;
    const parsed = JSON.parse(serialized) as Record<string, unknown>;
    const readDeck = (value: unknown, fallback: SavedDeckSettings): SavedDeckSettings => {
      if (!value || typeof value !== 'object') return fallback;
      const raw = value as Record<string, unknown>;
      return {
        volume:
          typeof raw.volume === 'number'
            ? clampMixerValue(raw.volume, 0, 1)
            : fallback.volume,
        pitch:
          typeof raw.pitch === 'number'
            ? clampMixerValue(raw.pitch, 0.5, 1.5)
            : fallback.pitch,
        effect:
          typeof raw.effect === 'string' &&
          SAVED_DJ_EFFECTS.includes(raw.effect as DJEffectType)
            ? (raw.effect as DJEffectType)
            : fallback.effect,
        fxAmount:
          typeof raw.fxAmount === 'number'
            ? clampMixerValue(raw.fxAmount, 0, 1)
            : fallback.fxAmount,
      };
    };
    const bank = parsed.selectedBank;
    return {
      crossfader:
        typeof parsed.crossfader === 'number'
          ? clampMixerValue(parsed.crossfader, 0, 1)
          : defaults.crossfader,
      selectedBank:
        bank === 'A' || bank === 'B' || bank === 'C' || bank === 'D'
          ? bank
          : defaults.selectedBank,
      samplerVolume:
        typeof parsed.samplerVolume === 'number'
          ? clampMixerValue(parsed.samplerVolume, 0, 1)
          : defaults.samplerVolume,
      deckA: readDeck(parsed.deckA, defaults.deckA),
      deckB: readDeck(parsed.deckB, defaults.deckB),
    };
  } catch {
    return defaults;
  }
}

interface TurntablePlatterProps {
  deckId: 'A' | 'B';
  accent: string;
  isPlaying: boolean;
  pitch: number;
  hasTrack: boolean;
  onBeginScratch: () => void;
  onScratchDelta: (deltaSeconds: number, velocity: number) => void;
  onEndScratch: () => void;
}

const TurntablePlatter: React.FC<TurntablePlatterProps> = ({
  deckId,
  accent,
  isPlaying,
  pitch,
  hasTrack,
  onBeginScratch,
  onScratchDelta,
  onEndScratch,
}) => {
  const discRef = useRef<HTMLDivElement>(null);
  const rotationRef = useRef(0);
  const [isScratching, setIsScratching] = useState(false);
  const dragState = useRef<{
    pointerId: number;
    lastAngle: number;
    lastTime: number;
  } | null>(null);

  useEffect(() => {
    let rafId = 0;
    let lastTs = performance.now();

    const tick = (now: number) => {
      const dt = (now - lastTs) / 1000;
      lastTs = now;
      if (isPlaying && !dragState.current && discRef.current) {
        rotationRef.current = (rotationRef.current + dt * 200 * pitch) % 360;
        discRef.current.style.transform = `rotate(${rotationRef.current}deg)`;
      }
      rafId = requestAnimationFrame(tick);
    };

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [isPlaying, pitch]);

  const computeAngle = (clientX: number, clientY: number) => {
    const rect = discRef.current?.getBoundingClientRect();
    if (!rect) return 0;
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    return Math.atan2(clientY - cy, clientX - cx) * (180 / Math.PI);
  };

  return (
    <div className="dj-platter-stage">
      <div className="dj-turntable-base" style={{ borderColor: `${accent}55` }}>
        <div
          className="dj-turntable-light"
          style={{
            background: `radial-gradient(circle at 28% 50%, ${accent}25, transparent 70%)`,
          }}
        />
        <div className="dj-vinyl-shadow" />
        <div
          ref={discRef}
          className={`dj-vinyl-disc ${isScratching ? 'is-scratching' : ''} ${
            !hasTrack ? 'is-empty' : ''
          }`}
          onPointerDown={(e) => {
            if (!hasTrack) return;
            e.currentTarget.setPointerCapture(e.pointerId);
            dragState.current = {
              pointerId: e.pointerId,
              lastAngle: computeAngle(e.clientX, e.clientY),
              lastTime: performance.now(),
            };
            setIsScratching(true);
            onBeginScratch();
          }}
          onPointerMove={(e) => {
            const st = dragState.current;
            if (!st || st.pointerId !== e.pointerId) return;
            const now = performance.now();
            const currentAngle = computeAngle(e.clientX, e.clientY);
            let deltaDeg = currentAngle - st.lastAngle;
            if (deltaDeg > 180) deltaDeg -= 360;
            if (deltaDeg < -180) deltaDeg += 360;
            const dt = Math.max(0.004, (now - st.lastTime) / 1000);
            st.lastAngle = currentAngle;
            st.lastTime = now;
            rotationRef.current = (rotationRef.current + deltaDeg) % 360;
            if (discRef.current) {
              discRef.current.style.transform = `rotate(${rotationRef.current}deg)`;
            }
            const deltaSeconds = (deltaDeg / 360) * 1.8;
            const velocity = deltaSeconds / dt;
            onScratchDelta(deltaSeconds, velocity);
          }}
          onPointerUp={(e) => {
            const st = dragState.current;
            if (!st || st.pointerId !== e.pointerId) return;
            dragState.current = null;
            setIsScratching(false);
            onEndScratch();
          }}
          onPointerCancel={() => {
            if (!dragState.current) return;
            dragState.current = null;
            setIsScratching(false);
            onEndScratch();
          }}
        >
          <div className="dj-vinyl-highlight" />
          <div
            className="dj-vinyl-label"
            style={{
              backgroundColor: accent,
              borderColor: '#ffffff55',
            }}
          >
            <span className="dj-vinyl-label-top">DJ PRO</span>
            <span className="dj-vinyl-label-deck">{deckId}</span>
            <span className="dj-vinyl-label-bottom">
              {Math.round(pitch * 100)}%
            </span>
          </div>
          <div className="dj-vinyl-spindle" />
        </div>
        <div className="dj-tonearm" aria-hidden="true">
          <span className="dj-tonearm-pivot" />
          <span className="dj-tonearm-shaft" />
          <span className="dj-tonearm-head" />
        </div>
        <div
          className="dj-turntable-led"
          style={{
            backgroundColor: isScratching
              ? '#ffb74d'
              : isPlaying
              ? '#00e676'
              : '#555b66',
          }}
        />
      </div>
    </div>
  );
};

interface DJMixerScreenProps {
  library: AudioItem[];
  themeColors: ThemeColors;
  isArabic: boolean;
  onPauseMainPlayer: () => void;
  onImportFiles: (files: FileList | File[]) => void;
}

export const DJMixerScreen: React.FC<DJMixerScreenProps> = ({
  library,
  themeColors,
  isArabic,
  onPauseMainPlayer,
  onImportFiles,
}) => {
  const [savedSettings] = useState<SavedMixerSettings>(() => readSavedMixerSettings());
  const [crossfader, setCrossfader] = useState(savedSettings.crossfader);
  const [deckAState, setDeckAState] = useState({
    track: deckAEngine.currentTrack,
    isPlaying: deckAEngine.isPlaying,
    currentBpm: deckAEngine.currentBpm,
    bpmConfidence: deckAEngine.bpmConfidence,
    loopBeats: deckAEngine.loopBeats,
    currentTime: deckAEngine.currentTimeMs,
    duration: deckAEngine.durationMs,
    pitch: deckAEngine.pitch,
    effect: deckAEngine.activeEffect,
    fxAmount: deckAEngine.fxAmount,
    volume: deckAEngine.volume,
  });

  const [deckBState, setDeckBState] = useState({
    track: deckBEngine.currentTrack,
    isPlaying: deckBEngine.isPlaying,
    currentBpm: deckBEngine.currentBpm,
    bpmConfidence: deckBEngine.bpmConfidence,
    loopBeats: deckBEngine.loopBeats,
    currentTime: deckBEngine.currentTimeMs,
    duration: deckBEngine.durationMs,
    pitch: deckBEngine.pitch,
    effect: deckBEngine.activeEffect,
    fxAmount: deckBEngine.fxAmount,
    volume: deckBEngine.volume,
  });

  const [selectedBank, setSelectedBank] = useState<MixerBank>(savedSettings.selectedBank);
  const [samplerVolume, setSamplerVolume] = useState(savedSettings.samplerVolume);
  const [trackSelectorDeck, setTrackSelectorDeck] = useState<'A' | 'B' | null>(null);
  const [beatSyncMessage, setBeatSyncMessage] = useState<string | null>(null);
  const [autoFillEnabled, setAutoFillEnabled] = useState(() => {
    try {
      return window.localStorage.getItem('dj-mixer-auto-fill-v1') === 'true';
    } catch {
      return false;
    }
  });
  const folderInputRef = useRef<HTMLInputElement>(null);

  const handleChooseLocalFolder = async () => {
    try {
      const files = await pickLocalAudioFolder();
      if (files === undefined) {
        folderInputRef.current?.click();
      } else if (files && files.length > 0) {
        onImportFiles(files);
      }
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    const unsubA = deckAEngine.subscribe(() => {
      setDeckAState({
        track: deckAEngine.currentTrack,
        isPlaying: deckAEngine.isPlaying,
        currentBpm: deckAEngine.currentBpm,
        bpmConfidence: deckAEngine.bpmConfidence,
        loopBeats: deckAEngine.loopBeats,
        currentTime: deckAEngine.currentTimeMs,
        duration: deckAEngine.durationMs,
        pitch: deckAEngine.pitch,
        effect: deckAEngine.activeEffect,
        fxAmount: deckAEngine.fxAmount,
        volume: deckAEngine.volume,
      });
    });

    const unsubB = deckBEngine.subscribe(() => {
      setDeckBState({
        track: deckBEngine.currentTrack,
        isPlaying: deckBEngine.isPlaying,
        currentBpm: deckBEngine.currentBpm,
        bpmConfidence: deckBEngine.bpmConfidence,
        loopBeats: deckBEngine.loopBeats,
        currentTime: deckBEngine.currentTimeMs,
        duration: deckBEngine.durationMs,
        pitch: deckBEngine.pitch,
        effect: deckBEngine.activeEffect,
        fxAmount: deckBEngine.fxAmount,
        volume: deckBEngine.volume,
      });
    });

    return () => {
      unsubA();
      unsubB();
    };
  }, []);

  const getNextAutoFillTrack = (
    current: AudioItem | null,
    other: AudioItem | null
  ): AudioItem | null => {
    if (library.length === 0) return null;
    const currentIndex = current
      ? library.findIndex((track) => track.id === current.id)
      : -1;
    for (let step = 1; step <= library.length; step += 1) {
      const index = (currentIndex + step + library.length) % library.length;
      const candidate = library[index];
      if (!candidate) continue;
      if (library.length === 1 || candidate.id !== other?.id) return candidate;
    }
    return library[0] || null;
  };

  useEffect(() => {
    try {
      window.localStorage.setItem('dj-mixer-auto-fill-v1', String(autoFillEnabled));
    } catch {
      // ignore
    }
    if (!autoFillEnabled || library.length === 0) return;

    if (!deckAEngine.currentTrack) {
      const nextA = getNextAutoFillTrack(null, deckBEngine.currentTrack);
      if (nextA) deckAEngine.loadTrack(nextA);
    }
    if (!deckBEngine.currentTrack) {
      const nextB = getNextAutoFillTrack(null, deckAEngine.currentTrack);
      if (nextB) deckBEngine.loadTrack(nextB);
    }

    const autoLoadAndPlay = (deck: 'A' | 'B') => {
      const engine = deck === 'A' ? deckAEngine : deckBEngine;
      const other = deck === 'A' ? deckBEngine : deckAEngine;
      const next = getNextAutoFillTrack(engine.currentTrack, other.currentTrack);
      if (!next) return;
      engine.loadTrack(next);
      void engine.play();
    };
    const offA = deckAEngine.onTrackEnded(() => autoLoadAndPlay('A'));
    const offB = deckBEngine.onTrackEnded(() => autoLoadAndPlay('B'));
    return () => {
      offA();
      offB();
    };
  }, [autoFillEnabled, library]);

  useEffect(() => {
    deckAEngine.setDeckVolume(savedSettings.deckA.volume);
    deckAEngine.setPitch(savedSettings.deckA.pitch);
    deckAEngine.setEffect(savedSettings.deckA.effect);
    deckAEngine.setFxAmount(savedSettings.deckA.fxAmount);
    deckBEngine.setDeckVolume(savedSettings.deckB.volume);
    deckBEngine.setPitch(savedSettings.deckB.pitch);
    deckBEngine.setEffect(savedSettings.deckB.effect);
    deckBEngine.setFxAmount(savedSettings.deckB.fxAmount);
    samplerEngine.volume = savedSettings.samplerVolume;
  }, [savedSettings]);

  useEffect(() => {
    try {
      const stateToSave: SavedMixerSettings = {
        crossfader,
        selectedBank,
        samplerVolume,
        deckA: {
          volume: deckAState.volume,
          pitch: deckAState.pitch,
          effect: deckAState.effect,
          fxAmount: deckAState.fxAmount,
        },
        deckB: {
          volume: deckBState.volume,
          pitch: deckBState.pitch,
          effect: deckBState.effect,
          fxAmount: deckBState.fxAmount,
        },
      };
      window.localStorage.setItem(DJ_MIXER_STATE_KEY, JSON.stringify(stateToSave));
    } catch {
      // ignore
    }
  }, [
    crossfader,
    selectedBank,
    samplerVolume,
    deckAState.volume,
    deckAState.pitch,
    deckAState.effect,
    deckAState.fxAmount,
    deckBState.volume,
    deckBState.pitch,
    deckBState.effect,
    deckBState.fxAmount,
  ]);

  useEffect(() => {
    const gainA = Math.cos((crossfader * Math.PI) / 2);
    const gainB = Math.sin((crossfader * Math.PI) / 2);
    deckAEngine.setCrossfadeVolume(gainA);
    deckBEngine.setCrossfadeVolume(gainB);
  }, [crossfader]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (['input', 'textarea'].includes((e.target as HTMLElement)?.tagName?.toLowerCase())) {
        return;
      }
      const key = e.key.toUpperCase();
      const currentPads = samplerEngine.pads[selectedBank];
      const match = currentPads.find((p) => p.hotkey === key);
      if (match) {
        samplerEngine.triggerPad(match);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedBank]);

  const handlePlayDeck = (deck: 'A' | 'B') => {
    onPauseMainPlayer();
    if (deck === 'A') {
      deckAEngine.togglePlay();
    } else {
      deckBEngine.togglePlay();
    }
  };

  const handleLoadTrack = (deck: 'A' | 'B', track: AudioItem) => {
    if (deck === 'A') {
      deckAEngine.loadTrack(track);
    } else {
      deckBEngine.loadTrack(track);
    }
    setTrackSelectorDeck(null);
  };

  const handleLoopBeats = (deck: 'A' | 'B', beats: number) => {
    const engine = deck === 'A' ? deckAEngine : deckBEngine;
    engine.setLoopBeats(beats);
  };

  const formatMs = (ms: number) => {
    if (!ms || isNaN(ms)) return '0:00';
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const effectsList: Array<{ id: DJEffectType; name: string }> = [
    { id: 'none', name: isArabic ? 'بدون مؤثر' : 'Bypass' },
    { id: 'fx_filter', name: isArabic ? 'فلتر ثنائي' : 'Dual Filter' },
    { id: 'fx_delay', name: isArabic ? 'صدى (Echo)' : 'Echo / Delay' },
    { id: 'fx_reverb', name: isArabic ? 'ريفريرب (Reverb)' : 'Reverb Hall' },
    { id: 'fx_flanger', name: isArabic ? 'فلانجر (Flanger)' : 'Flanger Jet' },
    { id: 'fx_phaser', name: isArabic ? 'فيزر (Phaser)' : 'Phaser' },
    { id: 'fx_bitcrush', name: isArabic ? 'لو فاي (8-Bit)' : '8-Bit Lo-Fi' },
    { id: 'fx_distortion', name: isArabic ? 'ديستورشن' : 'Overdrive' },
    { id: 'fx_compressor', name: isArabic ? 'كمبرسور' : 'Compressor' },
    { id: 'voice_chipmunk', name: isArabic ? 'سنجاب' : 'Chipmunk' },
    { id: 'voice_robot', name: isArabic ? 'روبوت' : 'Robot' },
    { id: 'voice_monster', name: isArabic ? 'وحش' : 'Monster' },
    { id: 'voice_demon', name: isArabic ? 'شبح' : 'Dark Demon' },
    { id: 'voice_giant', name: isArabic ? 'عملاق' : 'Giant Bass' },
    { id: 'voice_kid', name: isArabic ? 'طفل' : 'Kid Voice' },
    { id: 'voice_woman', name: isArabic ? 'أنثوي' : 'Woman Voice' },
    { id: 'voice_telephone', name: isArabic ? 'هاتف' : 'Telephone' },
    { id: 'voice_radio', name: isArabic ? 'راديو' : 'Vintage Radio' },
    { id: 'voice_alien', name: isArabic ? 'فضائي' : 'Alien Voice' },
  ];

  const handleBeatSync = () => {
    const bpmA = deckAState.currentBpm;
    const bpmB = deckBState.currentBpm;
    if (bpmA && bpmB && bpmA > 0 && bpmB > 0) {
      const targetRatio = clampMixerValue((bpmA / bpmB) * deckBState.pitch, 0.5, 1.5);
      deckBEngine.setPitch(targetRatio);
      setBeatSyncMessage(
        isArabic
          ? `تمت مزامنة الإيقاع (${Math.round(bpmA)} BPM)`
          : `Synced to ${Math.round(bpmA)} BPM!`
      );
    } else {
      deckBEngine.setPitch(deckAState.pitch);
      setBeatSyncMessage(
        isArabic ? 'تمت مطابقة سرعة الديك A و B' : 'Matched Deck B pitch to Deck A'
      );
    }
    setTimeout(() => setBeatSyncMessage(null), 2500);
  };

  const renderDeckCard = (
    deck: 'A' | 'B',
    state: typeof deckAState,
    engine: typeof deckAEngine,
    accent: string
  ) => (
    <div
      className="rounded-xl border p-2.5 flex flex-col justify-between min-h-0 overflow-hidden"
      style={{
        backgroundColor: themeColors.surface,
        borderColor: state.isPlaying ? accent : themeColors.border,
      }}
    >
      {/* Deck Header & Track Load */}
      <div className="flex items-center justify-between gap-2 shrink-0">
        <div className="flex items-center gap-2 min-w-0 flex-1">
          <span
            className="px-2 py-0.5 rounded-md text-xs font-black text-white shrink-0"
            style={{ backgroundColor: accent }}
          >
            DECK {deck}
          </span>
          <div className="min-w-0 flex-1">
            <div className="font-bold text-xs truncate">
              {state.track?.title || (isArabic ? 'لم يتم تحميل مسار' : 'No Track Loaded')}
            </div>
            <div className="text-[10px] opacity-60 truncate">
              {state.track?.artist || '---'} •{' '}
              {state.currentBpm ? `${Math.round(state.currentBpm)} BPM` : '-- BPM'}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={() => setTrackSelectorDeck(deck)}
            className="px-2 py-1 rounded-lg border text-[11px] font-bold flex items-center gap-1"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <FolderOpen className="w-3 h-3" style={{ color: accent }} />
            {isArabic ? 'مكتبة' : 'Load'}
          </button>

          <label
            className="px-2 py-1 rounded-lg border text-[11px] font-bold flex items-center gap-1 cursor-pointer"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <FileAudio className="w-3 h-3" style={{ color: accent }} />
            {isArabic ? 'ملف' : 'File'}
            <input
              type="file"
              accept={AUDIO_INPUT_ACCEPT}
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (!f) return;
                const url = URL.createObjectURL(f);
                const item: AudioItem = {
                  id: `dj_local_${Date.now()}`,
                  title: f.name.replace(/\.[^/.]+$/, ''),
                  artist: 'Local File',
                  album: `Deck ${deck}`,
                  duration: 0,
                  uri: url,
                  addedDate: Date.now(),
                };
                engine.loadTrack(item);
                onImportFiles([f]);
              }}
            />
          </label>
        </div>
      </div>

      {/* Middle Row: Turntable Platter + Transport & Sliders */}
      <div className="grid grid-cols-12 gap-2 items-center my-1 min-h-0 flex-1">
        <div className="col-span-5 flex flex-col items-center justify-center">
          <TurntablePlatter
            deckId={deck}
            accent={accent}
            isPlaying={state.isPlaying}
            pitch={state.pitch}
            hasTrack={Boolean(state.track)}
            onBeginScratch={() => {
              engine.startScratch();
            }}
            onScratchDelta={(d, v) =>
              engine.scratchTo(engine.currentTimeMs + d * 1000, v * 1000)
            }
            onEndScratch={() => engine.endScratch()}
          />
        </div>

        <div className="col-span-7 flex flex-col justify-between gap-1.5 h-full py-1">
          {/* Scrubber */}
          <div>
            <input
              type="range"
              min={0}
              max={state.duration || 100}
              value={state.currentTime || 0}
              onChange={(e) => engine.seekTo(Number(e.target.value))}
              className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
              style={{
                accentColor: accent,
                backgroundColor: themeColors.surfaceVariant,
              }}
            />
            <div className="flex justify-between text-[10px] font-mono opacity-70 mt-0.5">
              <span>{formatMs(state.currentTime)}</span>
              <span>{formatMs(state.duration)}</span>
            </div>
          </div>

          {/* Play / Cue / Loop Buttons */}
          <div className="flex items-center justify-between gap-1.5">
            <div className="flex items-center gap-1">
              <button
                onClick={() => handlePlayDeck(deck)}
                className="px-3 py-1.5 rounded-lg text-xs font-bold text-white flex items-center gap-1"
                style={{ backgroundColor: accent }}
              >
                {state.isPlaying ? (
                  <Pause className="w-3.5 h-3.5 fill-current" />
                ) : (
                  <Play className="w-3.5 h-3.5 fill-current" />
                )}
                {state.isPlaying ? 'PAUSE' : 'PLAY'}
              </button>
              <button
                onClick={() => engine.seekTo(0)}
                className="p-1.5 rounded-lg border"
                style={{
                  backgroundColor: themeColors.surfaceVariant,
                  borderColor: themeColors.border,
                }}
                title="Cue / Restart"
              >
                <RotateCcw className="w-3.5 h-3.5" />
              </button>
            </div>

            {/* Beat Loop Controls */}
            <div className="flex items-center gap-1">
              {[1, 2, 4, 8].map((b) => {
                const active = state.loopBeats === b;
                return (
                  <button
                    key={b}
                    onClick={() => handleLoopBeats(deck, b)}
                    className="px-1.5 py-1 rounded text-[10px] font-mono font-bold border"
                    style={{
                      backgroundColor: active ? accent : themeColors.surfaceVariant,
                      borderColor: active ? accent : themeColors.border,
                      color: active ? '#fff' : themeColors.textPrimary,
                    }}
                  >
                    {b}B
                  </button>
                );
              })}
            </div>
          </div>

          {/* Volume & Pitch Sliders */}
          <div className="grid grid-cols-2 gap-2">
            <div>
              <div className="flex justify-between text-[10px] font-bold">
                <span>VOL</span>
                <span style={{ color: accent }}>{Math.round(state.volume * 100)}%</span>
              </div>
              <input
                type="range"
                min={0}
                max={1}
                step={0.01}
                value={state.volume}
                onChange={(e) => engine.setDeckVolume(Number(e.target.value))}
                className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
                style={{ accentColor: accent }}
              />
            </div>

            <div>
              <div className="flex justify-between text-[10px] font-bold">
                <span>PITCH</span>
                <span style={{ color: accent }}>{Math.round(state.pitch * 100)}%</span>
              </div>
              <input
                type="range"
                min={0.5}
                max={1.5}
                step={0.01}
                value={state.pitch}
                onChange={(e) => engine.setPitch(Number(e.target.value))}
                className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
                style={{ accentColor: accent }}
              />
            </div>
          </div>

          {/* FX Selector & Wet/Dry Mix */}
          <div className="flex items-center gap-2">
            <select
              value={state.effect}
              onChange={(e) => engine.setEffect(e.target.value as DJEffectType)}
              className="px-2 py-1 rounded-lg border text-[11px] font-semibold outline-none flex-1 min-w-0"
              style={{
                backgroundColor: themeColors.background,
                borderColor: themeColors.border,
                color: themeColors.textPrimary,
              }}
            >
              {effectsList.map((fx) => (
                <option key={fx.id} value={fx.id}>
                  {fx.name}
                </option>
              ))}
            </select>
            <div className="flex items-center gap-1 w-28 shrink-0">
              <span className="text-[10px] font-bold">FX</span>
              <input
                type="range"
                min={0}
                max={1}
                step={0.01}
                value={state.fxAmount}
                onChange={(e) => engine.setFxAmount(Number(e.target.value))}
                className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
                style={{ accentColor: accent }}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div
      id="dj-mixer-screen"
      className="h-full min-h-0 flex flex-col justify-between overflow-hidden p-3 gap-2.5 select-none"
      style={{
        backgroundColor: themeColors.background,
        color: themeColors.textPrimary,
      }}
    >
      <input
        ref={folderInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(e) => {
          if (e.target.files && e.target.files.length > 0) {
            onImportFiles(e.target.files);
          }
        }}
      />

      {/* Top Mixer Control Bar (Title + Crossfader + Beat Sync + Auto Fill) */}
      <div
        className="rounded-xl p-2.5 border shrink-0 flex flex-wrap items-center justify-between gap-3"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2">
          <div
            className="w-8 h-8 rounded-lg flex items-center justify-center"
            style={{ backgroundColor: `${themeColors.primary}20` }}
          >
            <Disc className="w-4 h-4" style={{ color: themeColors.primary }} />
          </div>
          <h1 className="text-sm font-bold tracking-tight">
            {isArabic ? 'مكسر الدي جي الاحترافي' : 'DJ Studio Mixer'}
          </h1>
          {beatSyncMessage && (
            <span
              className="text-xs px-2 py-0.5 rounded font-bold"
              style={{
                backgroundColor: `${themeColors.primary}20`,
                color: themeColors.primary,
              }}
            >
              {beatSyncMessage}
            </span>
          )}
        </div>

        {/* Center Crossfader */}
        <div className="flex items-center gap-2.5 flex-1 max-w-md mx-auto">
          <span
            className="text-xs font-black"
            style={{ color: themeColors.accentA }}
          >
            A
          </span>
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={crossfader}
            onChange={(e) => setCrossfader(Number(e.target.value))}
            className="flex-1 h-2 rounded-lg appearance-none cursor-pointer"
            style={{
              accentColor: themeColors.primary,
              backgroundColor: themeColors.surfaceVariant,
            }}
          />
          <span
            className="text-xs font-black"
            style={{ color: themeColors.accentB }}
          >
            B
          </span>
          <button
            onClick={() => setCrossfader(0.5)}
            className="px-2 py-0.5 rounded text-[10px] font-bold border"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            CENTER
          </button>
        </div>

        {/* Right Actions: Beat Sync, Auto Fill, Import Folder */}
        <div className="flex items-center gap-1.5">
          <button
            onClick={handleBeatSync}
            className="px-2.5 py-1.5 rounded-lg text-xs font-bold text-white flex items-center gap-1"
            style={{ backgroundColor: themeColors.primary }}
          >
            <Sparkles className="w-3.5 h-3.5" />
            BEAT SYNC
          </button>

          <button
            onClick={() => setAutoFillEnabled((v) => !v)}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1"
            style={{
              backgroundColor: autoFillEnabled
                ? `${themeColors.primary}22`
                : themeColors.surfaceVariant,
              borderColor: autoFillEnabled ? themeColors.primary : themeColors.border,
              color: autoFillEnabled ? themeColors.primary : themeColors.textSecondary,
            }}
          >
            <Check className="w-3.5 h-3.5" />
            {isArabic ? 'تعبئة تلقائية' : 'Auto Fill'}
          </button>

          <button
            onClick={handleChooseLocalFolder}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <FolderPlus className="w-3.5 h-3.5" />
            {isArabic ? 'مجلد' : 'Folder'}
          </button>
        </div>
      </div>

      {/* Dual Decks Row (Deck A & Deck B Side-by-Side) */}
      <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-2 gap-2.5 overflow-hidden">
        {renderDeckCard('A', deckAState, deckAEngine, themeColors.accentA)}
        {renderDeckCard('B', deckBState, deckBEngine, themeColors.accentB)}
      </div>

      {/* Compact 16-Pad Sampler Dock (2x8 Grid) */}
      <div
        className="rounded-xl p-2.5 border shrink-0 flex flex-col gap-2"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Layers className="w-4 h-4" style={{ color: themeColors.primary }} />
            <span className="text-xs font-bold">
              {isArabic ? 'لوحة المؤثرات (16-Pad Sampler)' : '16-Pad FX Sampler'}
            </span>
            <div className="flex items-center gap-1 ml-2">
              {(['A', 'B', 'C', 'D'] as MixerBank[]).map((bank) => (
                <button
                  key={bank}
                  onClick={() => setSelectedBank(bank)}
                  className="px-2 py-0.5 rounded text-[11px] font-bold border"
                  style={{
                    backgroundColor:
                      selectedBank === bank
                        ? themeColors.primary
                        : themeColors.surfaceVariant,
                    borderColor:
                      selectedBank === bank ? themeColors.primary : themeColors.border,
                    color: selectedBank === bank ? '#fff' : themeColors.textPrimary,
                  }}
                >
                  BANK {bank}
                </button>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Volume2 className="w-3.5 h-3.5 opacity-70" />
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={samplerVolume}
              onChange={(e) => {
                const v = Number(e.target.value);
                setSamplerVolume(v);
                samplerEngine.volume = v;
              }}
              className="w-24 h-1.5 rounded-lg appearance-none cursor-pointer"
              style={{ accentColor: themeColors.primary }}
            />
            <button
              onClick={() => samplerEngine.stopAll()}
              className="px-2 py-0.5 rounded text-[10px] font-bold border text-red-400"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                borderColor: themeColors.border,
              }}
            >
              STOP FX
            </button>
          </div>
        </div>

        <div className="grid grid-cols-8 gap-1.5">
          {samplerEngine.pads[selectedBank].map((pad: SamplePad) => (
            <button
              key={pad.id}
              onClick={() => samplerEngine.triggerPad(pad)}
              className="px-2 py-1.5 rounded-lg border text-left transition-transform active:scale-95 flex items-center justify-between gap-1 overflow-hidden"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                borderColor: pad.color || themeColors.border,
              }}
            >
              <span className="text-[10px] font-bold truncate">{pad.name}</span>
              {pad.hotkey && (
                <span
                  className="text-[9px] font-mono px-1 rounded opacity-70 shrink-0"
                  style={{ backgroundColor: themeColors.background }}
                >
                  {pad.hotkey}
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Track Selector Modal */}
      {trackSelectorDeck && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div
            className="w-full max-w-lg rounded-2xl border p-4 flex flex-col max-h-[80vh]"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
              color: themeColors.textPrimary,
            }}
          >
            <div className="flex items-center justify-between pb-3 border-b" style={{ borderColor: themeColors.border }}>
              <h3 className="font-bold text-sm">
                {isArabic
                  ? `اختر أغنية لـ Deck ${trackSelectorDeck}`
                  : `Select Track for Deck ${trackSelectorDeck}`}
              </h3>
              <button onClick={() => setTrackSelectorDeck(null)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto py-2 space-y-1.5">
              {library.length === 0 ? (
                <div className="py-8 text-center text-xs opacity-60">
                  {isArabic ? 'المكتبة فارغة' : 'Library is empty'}
                </div>
              ) : (
                library.map((item) => (
                  <button
                    key={item.id}
                    onClick={() => handleLoadTrack(trackSelectorDeck, item)}
                    className="w-full p-2 rounded-lg border text-left flex items-center justify-between hover:opacity-90"
                    style={{
                      backgroundColor: themeColors.surfaceVariant,
                      borderColor: themeColors.border,
                    }}
                  >
                    <div className="min-w-0">
                      <div className="font-bold text-xs truncate">{item.title}</div>
                      <div className="text-[10px] opacity-60 truncate">{item.artist}</div>
                    </div>
                    <Play className="w-3.5 h-3.5 shrink-0" />
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

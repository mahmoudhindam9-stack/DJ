import React, { useEffect, useRef, useState } from 'react';
import {
  ChevronDown,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Repeat,
  Repeat1,
  Shuffle,
  Volume2,
  Heart,
  ListMusic,
  Sliders,
  Disc,
} from 'lucide-react';
import { AudioItem, PlaybackMode, ThemeColors } from '../types';
import { mainAudioEngine } from '../audio/AudioEngine';

interface NowPlayingModalProps {
  currentSong: AudioItem | null;
  isPlaying: boolean;
  currentTimeMs: number;
  durationMs: number;
  playbackMode: PlaybackMode;
  volume: number;
  themeColors: ThemeColors;
  isArabic: boolean;
  onClose: () => void;
  onPlayPause: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (ms: number) => void;
  onVolumeChange: (vol: number) => void;
  onTogglePlaybackMode: () => void;
  onToggleFavorite: (song: AudioItem) => void;
  onOpenQueue: () => void;
  onOpenEqualizer: () => void;
  onSendToDeckA?: (song: AudioItem) => void;
  onSendToDeckB?: (song: AudioItem) => void;
}

export const NowPlayingModal: React.FC<NowPlayingModalProps> = ({
  currentSong,
  isPlaying,
  currentTimeMs,
  durationMs,
  playbackMode,
  volume,
  themeColors,
  isArabic,
  onClose,
  onPlayPause,
  onNext,
  onPrev,
  onSeek,
  onVolumeChange,
  onTogglePlaybackMode,
  onToggleFavorite,
  onOpenQueue,
  onOpenEqualizer,
  onSendToDeckA,
  onSendToDeckB,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [visualizerMode, setVisualizerMode] = useState<
    'OFF' | 'SPECTRUM' | 'CIRCULAR' | 'WAVE' | 'AURORA' | 'GALAXY' | 'TUNNEL'
  >('SPECTRUM');

  useEffect(() => {
    let animId = 0;
    const canvas = canvasRef.current;
    if (!canvas || visualizerMode === 'OFF') return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = canvas.width;
    const height = canvas.height;
    const centerX = width / 2;
    const centerY = height / 2;
    const primary = themeColors.primary;
    const secondary = themeColors.secondary;
    const tertiary = themeColors.tertiary || themeColors.primary;

    const drawSmoothWave = (data: Uint8Array, yBase: number, amplitude: number, phase: number) => {
      ctx.beginPath();
      for (let i = 0; i < data.length; i++) {
        const x = (i / Math.max(1, data.length - 1)) * width;
        const signal = (data[i] / 255 - 0.5) * 2;
        const y = yBase + Math.sin((i / data.length) * Math.PI * 4 + phase) * amplitude * 0.22
          + signal * amplitude;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
    };

    const render = (time: number) => {
      const freqData = mainAudioEngine.getVisualizerData();
      ctx.clearRect(0, 0, width, height);
      const energy = freqData.length
        ? freqData.reduce((sum, value) => sum + value, 0) / (freqData.length * 255)
        : 0;
      const phase = time * 0.0012;

      if (visualizerMode === 'SPECTRUM') {
        const barWidth = (width / Math.max(1, freqData.length)) * 1.5;
        let x = 0;
        const gradient = ctx.createLinearGradient(0, height, 0, 0);
        gradient.addColorStop(0, primary);
        gradient.addColorStop(1, secondary);
        ctx.fillStyle = gradient;
        for (let i = 0; i < freqData.length; i++) {
          const barHeight = (freqData[i] / 255) * height * 0.9;
          ctx.fillRect(x, height - barHeight, Math.max(1, barWidth - 2), barHeight);
          x += barWidth;
        }
      } else if (visualizerMode === 'CIRCULAR') {
        const maxRadius = Math.min(width, height) * 0.34;
        const bars = Math.min(freqData.length, 48);
        for (let i = 0; i < bars; i++) {
          const angle = (i / bars) * Math.PI * 2 + phase * 0.2;
          const magnitude = (freqData[i] / 255) * height * 0.42;
          const inner = maxRadius;
          const outer = inner + magnitude;
          ctx.beginPath();
          ctx.moveTo(centerX + Math.cos(angle) * inner, centerY + Math.sin(angle) * inner);
          ctx.lineTo(centerX + Math.cos(angle) * outer, centerY + Math.sin(angle) * outer);
          ctx.strokeStyle = i % 2 ? secondary : primary;
          ctx.lineWidth = 2.2;
          ctx.stroke();
        }
        ctx.beginPath();
        ctx.arc(centerX, centerY, maxRadius * 0.72, 0, Math.PI * 2);
        ctx.strokeStyle = tertiary;
        ctx.globalAlpha = 0.35 + energy * 0.4;
        ctx.lineWidth = 1.5;
        ctx.stroke();
        ctx.globalAlpha = 1;
      } else if (visualizerMode === 'WAVE') {
        ctx.lineWidth = 2.3;
        const gradient = ctx.createLinearGradient(0, 0, width, 0);
        gradient.addColorStop(0, primary);
        gradient.addColorStop(0.5, secondary);
        gradient.addColorStop(1, tertiary);
        ctx.strokeStyle = gradient;
        drawSmoothWave(freqData, centerY, height * 0.42, phase);
        ctx.globalAlpha = 0.25;
        ctx.lineWidth = 5;
        drawSmoothWave(freqData, centerY, height * 0.42, phase);
        ctx.globalAlpha = 1;
      } else if (visualizerMode === 'AURORA') {
        for (let layer = 0; layer < 4; layer++) {
          const yOffset = height * (0.2 + layer * 0.17);
          ctx.beginPath();
          ctx.moveTo(0, height);
          for (let i = 0; i <= 60; i++) {
            const x = (i / 60) * width;
            const wave = Math.sin(i * 0.19 + phase * (0.55 + layer * 0.12)) * (height * 0.13)
              + Math.sin(i * 0.07 - phase * 0.7) * (height * 0.08);
            const amp = (freqData[(i * 3 + layer * 7) % Math.max(1, freqData.length)] || 0) / 255;
            const y = yOffset + wave - amp * height * 0.18;
            ctx.lineTo(x, y);
          }
          ctx.lineTo(width, height);
          ctx.closePath();
          ctx.fillStyle = [primary, secondary, tertiary, primary][layer];
          ctx.globalAlpha = 0.12 + energy * 0.14;
          ctx.fill();
        }
        ctx.globalAlpha = 1;
      } else if (visualizerMode === 'GALAXY') {
        const particles = 70;
        for (let i = 0; i < particles; i++) {
          const orbit = 8 + ((i * 17) % 55) + energy * 18;
          const angle = phase * (0.12 + (i % 5) * 0.035) + i * 2.399;
          const wobble = Math.sin(phase + i) * 3;
          const x = centerX + Math.cos(angle) * (orbit + wobble);
          const y = centerY + Math.sin(angle) * (orbit + wobble) * 0.62;
          const radius = 0.7 + ((freqData[i % Math.max(1, freqData.length)] || 0) / 255) * 2.1;
          ctx.beginPath();
          ctx.arc(x, y, radius, 0, Math.PI * 2);
          ctx.fillStyle = i % 3 === 0 ? tertiary : i % 2 ? primary : secondary;
          ctx.globalAlpha = 0.35 + energy * 0.55;
          ctx.fill();
        }
        ctx.globalAlpha = 1;
      } else if (visualizerMode === 'TUNNEL') {
        const rings = 9;
        for (let i = rings; i >= 1; i--) {
          const pulse = (time * 0.035 + i * 7) % 70;
          const radius = 5 + pulse * (0.55 + energy * 0.35);
          ctx.beginPath();
          ctx.ellipse(centerX, centerY, radius * 1.9, radius * 0.62, phase * 0.08, 0, Math.PI * 2);
          ctx.strokeStyle = i % 2 ? primary : secondary;
          ctx.globalAlpha = 0.2 + (1 - i / rings) * 0.5;
          ctx.lineWidth = 1.2 + energy * 1.4;
          ctx.stroke();
        }
        ctx.globalAlpha = 1;
      }

      animId = requestAnimationFrame(render);
    };

    render(0);
    return () => cancelAnimationFrame(animId);
  }, [themeColors, visualizerMode]);

  const formatTime = (ms: number) => {
    if (!ms || isNaN(ms)) return '0:00';
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  return (
    <div
      id="now-playing-fullscreen"
      className="fixed inset-0 z-50 flex flex-col justify-between p-4 backdrop-blur-2xl select-none overflow-hidden"
      style={{
        backgroundColor: `${themeColors.background}f6`,
        color: themeColors.textPrimary,
      }}
    >
      {/* Top Bar */}
      <div
        className="flex items-center justify-between pb-2.5 border-b shrink-0"
        style={{ borderColor: themeColors.border }}
      >
        <button
          onClick={onClose}
          className="p-1.5 rounded-full hover:bg-white/10 transition-colors"
          title="Minimize View"
        >
          <ChevronDown className="w-5 h-5" />
        </button>

        <span
          className="text-xs uppercase tracking-widest font-bold"
          style={{ color: themeColors.primary }}
        >
          {isArabic ? 'قيد التشغيل الآن' : 'Now Playing'}
        </span>

        <div className="flex items-center gap-1.5">
          <button
            onClick={onOpenEqualizer}
            className="p-1.5 rounded-full hover:bg-white/10 transition-colors"
            title="Equalizer"
          >
            <Sliders className="w-4 h-4" />
          </button>
          <button
            onClick={onOpenQueue}
            className="p-1.5 rounded-full hover:bg-white/10 transition-colors"
            title="Queue"
          >
            <ListMusic className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Main Content Area (Proportional & No Scroll) */}
      <div className="flex-1 min-h-0 flex flex-col items-center justify-evenly max-w-xl mx-auto w-full py-2 overflow-hidden">
        {/* Proportional Album Artwork */}
        <div
          className="relative rounded-2xl overflow-hidden shadow-2xl border flex items-center justify-center shrink-0"
          style={{
            width: 'clamp(120px, 28vh, 240px)',
            height: 'clamp(120px, 28vh, 240px)',
            borderColor: themeColors.border,
            backgroundColor: themeColors.surface,
          }}
        >
          {currentSong?.coverUri ? (
            <img
              src={currentSong.coverUri}
              alt={currentSong.title}
              className={`w-full h-full object-cover transition-all ${
                isPlaying ? 'scale-105' : 'scale-100'
              }`}
            />
          ) : (
            <Disc
              className={`w-20 h-20 ${isPlaying ? 'animate-spin' : ''}`}
              style={{
                color: themeColors.primary,
                animationDuration: '6s',
              }}
            />
          )}
        </div>

        {/* Android-matched six visualizer modes plus artwork-only mode */}
        <div className="w-full max-w-md shrink-0">
          <canvas
            ref={canvasRef}
            width={480}
            height={112}
            className={`w-full h-20 rounded-lg opacity-90 transition-opacity ${visualizerMode === 'OFF' ? 'opacity-0 h-0' : ''}`}
            aria-label="Live audio visualizer"
          />
          <div className="mt-1.5 flex flex-wrap items-center justify-center gap-1">
            {(['OFF', 'SPECTRUM', 'CIRCULAR', 'WAVE', 'AURORA', 'GALAXY', 'TUNNEL'] as const).map((mode) => (
              <button
                key={mode}
                onClick={() => setVisualizerMode(mode)}
                className="rounded-full border px-2 py-1 text-[9px] font-bold tracking-wide transition-colors"
                style={{
                  borderColor: visualizerMode === mode ? themeColors.primary : themeColors.border,
                  color: visualizerMode === mode ? '#fff' : themeColors.textMuted,
                  backgroundColor: visualizerMode === mode ? themeColors.primary : themeColors.surfaceVariant,
                }}
                aria-pressed={visualizerMode === mode}
              >
                {mode === 'OFF' ? (isArabic ? 'الغلاف' : 'ART') : mode}
              </button>
            ))}
          </div>
        </div>

        {/* Song Info & Favorite Button */}
        <div className="flex items-center justify-between w-full px-2 shrink-0">
          <div className="min-w-0 flex-1">
            <h2 className="text-lg sm:text-xl font-bold truncate leading-tight">
              {currentSong?.title || (isArabic ? 'لا توجد أغنية' : 'No track loaded')}
            </h2>
            <p
              className="text-xs font-medium truncate"
              style={{ color: themeColors.textMuted }}
            >
              {currentSong?.artist || 'DJ Studio'}
            </p>
          </div>
          {currentSong && (
            <button
              onClick={() => onToggleFavorite(currentSong)}
              className="p-2 rounded-full hover:bg-white/10 transition-colors ml-3 shrink-0"
              title="Favorite"
            >
              <Heart
                className="w-5 h-5 transition-all"
                style={{
                  color: currentSong.isFavorite ? '#ff1744' : themeColors.textMuted,
                  fill: currentSong.isFavorite ? '#ff1744' : 'none',
                }}
              />
            </button>
          )}
        </div>

        {/* Progress Scrubber */}
        <div className="w-full px-2 shrink-0">
          <input
            type="range"
            min={0}
            max={durationMs || 100}
            value={currentTimeMs || 0}
            onChange={(e) => onSeek(Number(e.target.value))}
            className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
            style={{
              accentColor: themeColors.primary,
              backgroundColor: themeColors.surfaceVariant,
            }}
          />
          <div
            className="flex justify-between text-[11px] mt-1"
            style={{ color: themeColors.textMuted }}
          >
            <span>{formatTime(currentTimeMs)}</span>
            <span>{formatTime(durationMs)}</span>
          </div>
        </div>

        {/* Playback Controls */}
        <div className="flex items-center justify-center gap-5 shrink-0">
          <button
            onClick={onTogglePlaybackMode}
            className="p-2 rounded-full hover:bg-white/10 transition-colors"
            style={{
              color:
                playbackMode !== 'NORMAL' ? themeColors.primary : themeColors.textMuted,
            }}
          >
            {playbackMode === 'REPEAT_ONE' ? (
              <Repeat1 className="w-4 h-4" />
            ) : playbackMode === 'SHUFFLE' ? (
              <Shuffle className="w-4 h-4" />
            ) : (
              <Repeat className="w-4 h-4" />
            )}
          </button>

          <button
            onClick={onPrev}
            className="p-2.5 rounded-full hover:bg-white/10 transition-colors"
            title="Previous"
          >
            <SkipBack className="w-6 h-6" />
          </button>

          <button
            onClick={onPlayPause}
            className="p-4 rounded-full shadow-2xl transition-transform active:scale-95"
            style={{
              backgroundColor: themeColors.primary,
              color: '#ffffff',
            }}
            title={isPlaying ? 'Pause' : 'Play'}
          >
            {isPlaying ? (
              <Pause className="w-7 h-7 fill-current" />
            ) : (
              <Play className="w-7 h-7 fill-current ml-0.5" />
            )}
          </button>

          <button
            onClick={onNext}
            className="p-2.5 rounded-full hover:bg-white/10 transition-colors"
            title="Next"
          >
            <SkipForward className="w-6 h-6" />
          </button>

          <div className="flex items-center gap-1.5">
            <Volume2 className="w-4 h-4 opacity-70" />
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={volume}
              onChange={(e) => onVolumeChange(Number(e.target.value))}
              className="w-20 h-1.5 rounded-lg appearance-none cursor-pointer"
              style={{ accentColor: themeColors.primary }}
            />
          </div>
        </div>

        {/* Quick Send to DJ Decks */}
        {currentSong && (
          <div className="flex items-center gap-2.5 shrink-0">
            <button
              onClick={() => onSendToDeckA && onSendToDeckA(currentSong)}
              className="px-3 py-1 rounded-lg text-xs font-bold border transition-colors flex items-center gap-1.5"
              style={{
                backgroundColor: `${themeColors.accentA}20`,
                borderColor: themeColors.accentA,
                color: themeColors.accentA,
              }}
            >
              <Disc className="w-3.5 h-3.5" />
              Deck A
            </button>

            <button
              onClick={() => onSendToDeckB && onSendToDeckB(currentSong)}
              className="px-3 py-1 rounded-lg text-xs font-bold border transition-colors flex items-center gap-1.5"
              style={{
                backgroundColor: `${themeColors.accentB}20`,
                borderColor: themeColors.accentB,
                color: themeColors.accentB,
              }}
            >
              <Disc className="w-3.5 h-3.5" />
              Deck B
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

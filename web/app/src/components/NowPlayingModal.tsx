import React, { useEffect, useRef } from 'react';
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

  useEffect(() => {
    let animId: number;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const render = () => {
      const freqData = mainAudioEngine.getVisualizerData();
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      const barWidth = (canvas.width / freqData.length) * 1.5;
      let x = 0;

      for (let i = 0; i < freqData.length; i++) {
        const barHeight = (freqData[i] / 255) * canvas.height * 0.9;
        const gradient = ctx.createLinearGradient(0, canvas.height, 0, 0);
        gradient.addColorStop(0, themeColors.primary);
        gradient.addColorStop(1, themeColors.secondary);

        ctx.fillStyle = gradient;
        ctx.fillRect(x, canvas.height - barHeight, barWidth - 2, barHeight);
        x += barWidth;
      }

      animId = requestAnimationFrame(render);
    };

    render();
    return () => cancelAnimationFrame(animId);
  }, [themeColors]);

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

        {/* Live FFT Visualizer Canvas */}
        <canvas
          ref={canvasRef}
          width={480}
          height={48}
          className="w-full max-w-md h-10 rounded-lg opacity-80 shrink-0"
        />

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
